package main

import cats.effect.{IO, IOApp}
import cats.syntax.semigroupk.*
import config.AuthServerConfig
import interpreters.{AuthInterpreter, UserRepoInterpreter, AuditLogInterpreter}
import routes.{AuthRoutes, AdminRoutes, ValidateRoutes}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.uri
import org.http4s.headers.Location
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{Logger, CORS}
import com.comcast.ip4s.*
import fs2.io.net.tls.TLSContext
import java.nio.file.Paths

object Main extends IOApp.Simple {

  def run: IO[Unit] = {
    val cfg = AuthServerConfig.load()
    val userRepo = UserRepoInterpreter(cfg)
    val auditLog = AuditLogInterpreter(cfg.auditLogPath)
    val auth = AuthInterpreter(cfg, userRepo, auditLog)

    val promoteAdmin: IO[Unit] = userRepo.findByLogin("admin").flatMap {
      case Some(user) if user.role != "admin" =>
        userRepo.update(user.copy(role = "admin")).flatMap { _ =>
          IO.println("User 'admin' promoted to admin role")
        }
      case _ => IO.unit
    }

    val validateRoutes = ValidateRoutes(auth).routes
    val viewRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "login"    => routes.html.loginPage(None)
      case GET -> Root / "register" => routes.html.registerPage(None)
      case GET -> Root              => SeeOther(Location(uri"https://194.67.92.112:8082/files"))
    }
    val authRoutes  = AuthRoutes(auth).routes
    val adminRoutes = AdminRoutes(auth).routes

    val cors = CORS.policy.withAllowOriginAll
    val app = Logger.httpApp(true, true)(
      cors((validateRoutes <+> viewRoutes <+> authRoutes <+> adminRoutes).orNotFound)
    )

    promoteAdmin *> TLSContext.Builder.forAsync[IO]
      .fromKeyStoreFile(Paths.get("keystore.p12"), "123456".toCharArray, "123456".toCharArray)
      .flatMap { tlsContext =>
        EmberServerBuilder.default[IO]
          .withHost(Host.fromString(cfg.host).get)
          .withPort(Port.fromInt(cfg.port).get)
          .withHttpApp(app)
          .withTLS(tlsContext)
          .build
          .use(_ => IO.never)
      }
  }
}