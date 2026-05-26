package interpreters

import cats.effect.IO
import algebras.AuthClient
import domain.UserInfo
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import java.net.{HttpURLConnection, URI}
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}
import java.security.cert.X509Certificate

class AuthClientInterpreter(authServiceUrl: String) extends AuthClient[IO] {

  // Создаём SSLContext, который доверяет всем сертификатам (для разработки)
  private val trustingSSLContext: SSLContext = {
    val trustManager = new X509TrustManager {
      def getAcceptedIssuers: Array[X509Certificate] = Array.empty
      def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
      def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
    }
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, Array(trustManager), null)
    ctx
  }

  private def makeRequest(path: String, token: String): IO[String] = IO.blocking {
    val url = new URI(s"$authServiceUrl$path").toURL
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("GET")
    conn.setRequestProperty("Authorization", s"Bearer $token")
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(5000)

    // Применяем доверяющий SSLContext
    conn match {
      case httpsConn: javax.net.ssl.HttpsURLConnection =>
        httpsConn.setSSLSocketFactory(trustingSSLContext.getSocketFactory)
        httpsConn.setHostnameVerifier((_, _) => true)
      case _ => ()
    }

    val status = conn.getResponseCode
    if (status == 200) {
      scala.io.Source.fromInputStream(conn.getInputStream).mkString
    } else {
      throw new RuntimeException(s"Request failed with status $status")
    }
  }

  def validateToken(token: String): IO[Option[UserInfo]] =
    makeRequest("/validate", token).map { response =>
      decode[UserInfo](response).toOption
    }.handleError(_ => None)

  def listUsers(token: String): IO[Option[List[UserInfo]]] =
    makeRequest("/admin/users", token).map { response =>
      decode[List[UserInfo]](response).toOption
    }.handleError(_ => None)
}