package routes

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.Location
import org.http4s.implicits.uri
import algebras.{FileStorage, AuthClient, AuditLog}
import domain.{FileMeta, UserInfo}
import java.util.UUID

class AdminPageRoutes(storage: FileStorage[IO], authClient: AuthClient[IO], auditLog: AuditLog[IO]) {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "admin" => renderAdminPage(req)
  }

  private def extractToken(req: Request[IO]): Option[String] =
    req.cookies.find(_.name == "jwt").map(_.content)

  private def userInfoFromCookie(req: Request[IO]): IO[Option[UserInfo]] = {
    extractToken(req) match {
      case None    => IO.pure(None)
      case Some(t) => authClient.validateToken(t)
    }
  }

  private def renderAdminPage(req: Request[IO]): IO[Response[IO]] = {
    userInfoFromCookie(req).flatMap {
      case Some(user) if user.role == "admin" =>
        val token = extractToken(req).getOrElse("")
        for {
          users   <- authClient.listUsers(token).map(_.getOrElse(List.empty[UserInfo]))
          allFiles <- storage.listFiles(user.id, None)
          logs    <- auditLog.list(20).map(entries => entries.map(_.details))
          resp    <- html.adminPage(users, allFiles, logs)
        } yield resp
      case _ => SeeOther(Location(uri"https://localhost:8081/login"))
    }
  }
}