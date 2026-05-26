package routes

import cats.effect.IO
import config.FileServerConfig
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.*
import org.http4s.implicits.uri
import org.http4s.multipart.{Multipart, Part}
import org.typelevel.ci.CIString
import algebras.{FileStorage, AuthClient, AuditLog, AuditEntry}
import domain.{FileError, FileMeta, UserInfo}
import java.util.UUID
import java.time.Instant
import scala.concurrent.duration.*
import io.circe.syntax.*
import io.circe.generic.auto.*

class FileRoutes(storage: FileStorage[IO], authClient: AuthClient[IO], auditLog: AuditLog[IO], cfg: FileServerConfig) {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root                          => SeeOther(Location(uri"https://194.67.92.112:8081/login"))
    case req @ GET -> Root / "files"          => listFiles(req)
    case req @ GET -> Root / "upload"         => html.uploadPage(None)
    case req @ POST -> Root / "upload"        => uploadFile(req)
    case req @ GET -> Root / "download" / id  => downloadPage(req, id)
    case req @ POST -> Root / "download" / id => downloadFile(req, id)
    case req @ GET -> Root / "delete" / id    => deletePage(req, id)
    case req @ POST -> Root / "delete" / id   => deleteFile(req, id)
    case req @ GET -> Root / "search"         => searchFiles(req)
    case req @ POST -> Root / "share" / id    => shareFile(req, id)
    case req @ GET -> Root / "shared" / token => downloadShared(req, token)
    case req @ GET -> Root / "logout"         => logout(req)
    case req @ DELETE -> Root / "files" / id  => adminDeleteFile(req, id)
  }

  private def userIdFromCookie(req: Request[IO]): IO[Option[UUID]] = {
    req.cookies.find(_.name == "jwt") match {
      case None    => IO.pure(None)
      case Some(c) => authClient.validateToken(c.content).map(_.map(_.id))
    }
  }

  private def userInfoFromCookie(req: Request[IO]): IO[Option[UserInfo]] = {
    req.cookies.find(_.name == "jwt") match {
      case None    => IO.pure(None)
      case Some(c) => authClient.validateToken(c.content)
    }
  }

  private def withAuth(req: Request[IO])(onSuccess: UUID => IO[Response[IO]]): IO[Response[IO]] = {
    userIdFromCookie(req).flatMap {
      case None     => SeeOther(Location(uri"https://localhost:8081/login"))
      case Some(id) => onSuccess(id)
    }
  }

  private def listFiles(req: Request[IO]): IO[Response[IO]] = {
    withAuth(req) { userId =>
      userInfoFromCookie(req).flatMap { userInfo =>
        val isAdmin = userInfo.exists(_.role == "admin")
        storage.listFiles(userId, None).flatMap { files =>
          html.filesPage(files, userId, isAdmin)
        }
      }
    }
  }

  private def uploadFile(req: Request[IO]): IO[Response[IO]] = {
    withAuth(req) { userId =>
      userInfoFromCookie(req).flatMap {
        case None => Forbidden("Not authenticated")
        case Some(userInfo) =>
          req.as[Multipart[IO]].flatMap { mp =>
            val filePart = mp.parts.find(_.name == Some("file"))
            val passwordPart = mp.parts.find(_.name == Some("password"))
            (filePart, passwordPart) match {
              case (Some(file), Some(pass)) =>
                val fileName = file.filename.getOrElse("unnamed")
                val password = pass.body.through(fs2.text.utf8.decode).compile.string
                password.flatMap { pwd =>
                  storage.uploadFile(userId, fileName, None, pwd.trim, userInfo.login, file.body).flatMap {
                    case Right(_) =>
                      auditLog.log(AuditEntry(Instant.now, "UPLOAD", Some(userId.toString), req.remoteAddr.map(_.toString),
                          s"Пользователь ${userInfo.login} загрузил файл $fileName"))
                        .flatMap(_ => SeeOther(Location(uri"/files")))
                    case Left(_) => html.uploadPage(Some("Ошибка загрузки"))
                  }
                }
              case _ => BadRequest("Отсутствует файл или пароль")
            }
          }.recoverWith { case _ => BadRequest("Неверное тело запроса") }
      }
    }
  }

  private def downloadPage(req: Request[IO], id: String): IO[Response[IO]] = {
    withAuth(req) { _ =>
      storage.getFileMeta(UUID.fromString(id)).flatMap {
        case Some(meta) => html.downloadPage(meta, None)
        case None        => NotFound("Файл не найден")
      }
    }
  }

  private def downloadFile(req: Request[IO], id: String): IO[Response[IO]] = {
    withAuth(req) { userId =>
      userInfoFromCookie(req).flatMap { userInfo =>
        val login = userInfo.map(_.login).getOrElse("неизвестный")
        req.as[UrlForm].flatMap { form =>
          val password = form.getFirstOrElse("password", "")
          storage.downloadFile(UUID.fromString(id), password).flatMap {
            case Right((meta, stream)) =>
              auditLog.log(AuditEntry(Instant.now, "DOWNLOAD", Some(userId.toString), req.remoteAddr.map(_.toString),
                  s"Пользователь $login скачал файл ${meta.originalName}"))
                .flatMap(_ =>
                  Ok(stream)
                    .map(_.withContentType(`Content-Type`(MediaType.application.`octet-stream`))
                      .putHeaders(`Content-Disposition`("attachment", Map(CIString("filename") -> meta.originalName)))))
            case Left(FileError.FileNotFound)  => NotFound("Файл не найден")
            case Left(FileError.WrongPassword) => SeeOther(Location(uri"/download".addPath(id)))
            case Left(_)                       => InternalServerError("Ошибка")
          }
        }
      }
    }
  }

  private def deletePage(req: Request[IO], id: String): IO[Response[IO]] = {
    withAuth(req) { _ =>
      storage.getFileMeta(UUID.fromString(id)).flatMap {
        case Some(meta) => html.deletePage(meta)
        case None        => NotFound("Файл не найден")
      }
    }
  }

  private def deleteFile(req: Request[IO], id: String): IO[Response[IO]] = {
    withAuth(req) { userId =>
      userInfoFromCookie(req).flatMap { userInfo =>
        val login = userInfo.map(_.login).getOrElse("неизвестный")
        req.as[UrlForm].flatMap { form =>
          val password = form.getFirstOrElse("password", "")
          storage.downloadFile(UUID.fromString(id), password).flatMap {
            case Right((meta, _)) =>
              storage.deleteFile(UUID.fromString(id), userId).flatMap {
                case Right(_) =>
                  auditLog.log(AuditEntry(Instant.now, "DELETE", Some(userId.toString), req.remoteAddr.map(_.toString),
                      s"Пользователь $login удалил файл ${meta.originalName}"))
                    .flatMap(_ => SeeOther(Location(uri"/files")))
                case Left(_) => Forbidden("Вы не можете удалить этот файл")
              }
            case Left(FileError.WrongPassword) => SeeOther(Location(uri"/delete".addPath(id)))
            case Left(_)                       => Forbidden("Вы не можете удалить этот файл")
          }
        }
      }
    }
  }

  private def searchFiles(req: Request[IO]): IO[Response[IO]] = {
    withAuth(req) { userId =>
      val query = req.params.getOrElse("q", "")
      storage.searchFiles(userId, query).flatMap(results => html.searchPage(results, query))
    }
  }

  private def shareFile(req: Request[IO], id: String): IO[Response[IO]] = {
    withAuth(req) { userId =>
      storage.createShareLink(UUID.fromString(id), userId, 24.hours).flatMap {
        case Right(link) => Ok(s"Ссылка для общего доступа: /shared/${link.token}")
        case Left(_)     => NotFound("Файл не найден")
      }
    }
  }

  private def downloadShared(req: Request[IO], token: String): IO[Response[IO]] = {
    storage.getShareLink(token).flatMap {
      case None => NotFound("Ссылка не найдена")
      case Some(link) if link.expiresAt.isBefore(Instant.now) =>
        storage.deleteShareLink(token).flatMap(_ => Gone("Срок действия ссылки истёк"))
      case Some(link) =>
        storage.downloadFile(link.fileId, "").flatMap {
          case Right((meta, stream)) =>
            Ok(stream)
              .map(_.withContentType(`Content-Type`(MediaType.application.`octet-stream`))
                .putHeaders(`Content-Disposition`("attachment", Map(CIString("filename") -> meta.originalName))))
          case Left(_) => InternalServerError("Файл отсутствует")
        }
    }
  }

  private def logout(req: Request[IO]): IO[Response[IO]] = {
    SeeOther(Location(uri"https://194.67.92.112:8081/login"))
      .map(_.addCookie(ResponseCookie("jwt", "", path = Some("/"), maxAge = Some(0), httpOnly = true, sameSite = Some(SameSite.Lax))))
  }

  private def adminDeleteFile(req: Request[IO], id: String): IO[Response[IO]] = {
    userInfoFromCookie(req).flatMap {
      case Some(user) if user.role == "admin" =>
        storage.adminDeleteFile(UUID.fromString(id)).flatMap {
          case Right(_) =>
            auditLog.log(AuditEntry(Instant.now, "ADMIN_DELETE", Some(user.id.toString), req.remoteAddr.map(_.toString),
                s"Администратор ${user.login} удалил файл $id"))
              .flatMap(_ => Ok("Deleted"))
          case Left(_)  => NotFound("File not found")
        }
      case _ => Forbidden("Admin access required")
    }
  }
}