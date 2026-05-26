package main

import cats.effect.{IO, IOApp}
import cats.syntax.semigroupk.*
import config.FileServerConfig
import interpreters.{FileStorageInterpreter, AuthClientInterpreter, AuditLogInterpreter}
import routes.{FileRoutes, AdminPageRoutes}
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{Logger, CORS}
import com.comcast.ip4s.*
import fs2.io.net.tls.TLSContext
import java.nio.file.Paths

object Main extends IOApp.Simple {

  def run: IO[Unit] = {
    val cfg = FileServerConfig.load()
    val storage = FileStorageInterpreter(cfg)
    val authClient = AuthClientInterpreter(cfg.authServiceUrl)
    val auditLog = AuditLogInterpreter(cfg.auditLogPath)
    val fileRoutes = FileRoutes(storage, authClient, auditLog, cfg).routes
    val adminPageRoutes = AdminPageRoutes(storage, authClient, auditLog).routes

    val cors = CORS.policy.withAllowOriginAll
    val app = Logger.httpApp(true, true)(
      cors((adminPageRoutes <+> fileRoutes).orNotFound)
    )

    TLSContext.Builder.forAsync[IO]
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