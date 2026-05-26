package interpreters

import cats.effect.{IO, Resource}
import org.http4s.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.circe.CirceEntityDecoder.*
import algebras.AuthClient
import domain.UserInfo
import org.typelevel.ci.CIString
import io.circe.generic.auto.*
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}
import java.security.cert.X509Certificate

class AuthClientInterpreter(authServiceUrl: String) extends AuthClient[IO] {

  private val _ = {
    val trustManager = new X509TrustManager {
      def getAcceptedIssuers: Array[X509Certificate] = Array.empty
      def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
      def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
    }
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, Array(trustManager), null)
    SSLContext.setDefault(ctx)
  }

  private val clientResource: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build

  def validateToken(token: String): IO[Option[UserInfo]] =
    clientResource.use { httpClient =>
      val request = Request[IO](Method.POST, Uri.unsafeFromString(s"$authServiceUrl/validate"))
        .withHeaders(Header.Raw(CIString("Authorization"), s"Bearer $token"))
      httpClient.run(request).use { response =>
        response.status match {
          case Status.Ok => response.as[UserInfo].map(Some(_))
          case _         => IO.pure(None)
        }
      }
    }

  def listUsers(token: String): IO[Option[List[UserInfo]]] =
    clientResource.use { httpClient =>
      val request = Request[IO](Method.GET, Uri.unsafeFromString(s"$authServiceUrl/admin/users"))
        .withHeaders(Header.Raw(CIString("Authorization"), s"Bearer $token"))
      httpClient.run(request).use { response =>
        response.status match {
          case Status.Ok => response.as[List[UserInfo]].map(Some(_))
          case _         => IO.pure(None)
        }
      }
    }
}