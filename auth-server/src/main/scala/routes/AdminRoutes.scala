package routes

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityEncoder.*
import algebras.Auth
import domain.{UserInfo, AuthError}
import java.util.UUID
import io.circe.generic.auto.*
import org.typelevel.ci.CIString

class AdminRoutes(auth: Auth[IO]) {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "admin" / "users"              => listUsers(req)
    case req @ POST -> Root / "admin" / "users" / id / "delete" => deleteUser(req, id)
    case req @ POST -> Root / "admin" / "users" / id / "change-password" => changePassword(req, id)
  }

  private def extractToken(req: Request[IO]): Option[String] =
    req.headers.get(CIString("Authorization")).map(_.head.value.stripPrefix("Bearer ").trim)

  private def withAdmin(req: Request[IO])(onSuccess: UUID => IO[Response[IO]]): IO[Response[IO]] = {
    extractToken(req) match {
      case None => Forbidden("Missing token")
      case Some(token) =>
        auth.validateToken(token).flatMap {
          case Right(user) if user.role == "admin" => onSuccess(user.id)
          case _ => Forbidden("Admin access required")
        }
    }
  }

  private def listUsers(req: Request[IO]): IO[Response[IO]] = {
    withAdmin(req) { adminId =>
      auth.listUsers(adminId).flatMap {
        case Right(users) => Ok(users)
        case Left(error)  => Forbidden(error.message)
      }
    }
  }

  private def deleteUser(req: Request[IO], id: String): IO[Response[IO]] = {
    withAdmin(req) { adminId =>
      auth.deleteUser(adminId, UUID.fromString(id)).flatMap {
        case Right(_)    => Ok("User deleted")
        case Left(error) => Forbidden(error.message)
      }
    }
  }

  private def changePassword(req: Request[IO], id: String): IO[Response[IO]] = {
    withAdmin(req) { adminId =>
      req.as[UrlForm].flatMap { form =>
        val newPassword = form.getFirstOrElse("password", "")
        auth.changePassword(adminId, UUID.fromString(id), newPassword).flatMap {
          case Right(_)    => Ok("Password changed")
          case Left(error) => Forbidden(error.message)
        }
      }
    }
  }
}