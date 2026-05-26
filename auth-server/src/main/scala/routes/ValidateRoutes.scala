package routes

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityEncoder.*
import algebras.Auth
import org.typelevel.ci.CIString
import io.circe.generic.auto.*

class ValidateRoutes(auth: Auth[IO]) {
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "validate" =>
      req.headers.get(CIString("Authorization")).map(_.head.value) match {
        case Some(headerValue) if headerValue.startsWith("Bearer ") =>
          val token = headerValue.stripPrefix("Bearer ").trim
          auth.validateToken(token).flatMap {
            case Right(user) => Ok(user)
            case Left(_)     => Forbidden("Invalid token")
          }
        case _ => BadRequest("Missing or invalid Authorization header")
      }
  }
}