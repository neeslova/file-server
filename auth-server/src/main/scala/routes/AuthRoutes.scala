package routes

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.uri
import org.http4s.headers.Location
import algebras.Auth
import domain.{LoginRequest, RegisterRequest}

class AuthRoutes(auth: Auth[IO]) {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "login"    => handleLogin(req)
    case req @ POST -> Root / "register" => handleRegister(req)
  }

  private def handleLogin(req: Request[IO]): IO[Response[IO]] = {
    req.as[UrlForm].flatMap { form =>
      val loginReq = LoginRequest(form.getFirstOrElse("login", ""), form.getFirstOrElse("password", ""))
      auth.login(loginReq).flatMap {
        case Left(error) => html.loginPage(Some(error.message))
        case Right(tokens) =>
          SeeOther(Location(uri"https://localhost:8082/files"))
            .map(_.addCookie(ResponseCookie("jwt", tokens.accessToken, path = Some("/"))))
      }
    }
  }

  private def handleRegister(req: Request[IO]): IO[Response[IO]] = {
    req.as[UrlForm].flatMap { form =>
      val regReq = RegisterRequest(form.getFirstOrElse("login", ""), form.getFirstOrElse("password", ""))
      auth.register(regReq).flatMap {
        case Left(error) => html.registerPage(Some(error.message))
        case Right(_)    => SeeOther(Location(uri"https://localhost:8081/login"))
      }
    }
  }
}