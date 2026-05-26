package config

final case class AuthServerConfig(
                                   host: String,
                                   port: Int,
                                   jwtSecret: String,
                                   userDbPath: String,
                                   userDbKey: String,
                                   auditLogPath: String
                                 )

object AuthServerConfig:
  def load(): AuthServerConfig = AuthServerConfig(
    host          = sys.env.getOrElse("AUTH_HOST", "0.0.0.0"),
    port          = sys.env.getOrElse("AUTH_PORT", "8081").toInt,
    jwtSecret     = sys.env.getOrElse("JWT_SECRET", "change-me"),
    userDbPath    = sys.env.getOrElse("USER_DB_PATH", "users.enc"),
    userDbKey     = sys.env.getOrElse("USER_DB_KEY", "0123456789abcdef"),
    auditLogPath  = sys.env.getOrElse("AUDIT_LOG_PATH", "audit.log")
  )