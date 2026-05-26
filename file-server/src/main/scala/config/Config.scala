package config

final case class FileServerConfig(
                                   host: String,
                                   port: Int,
                                   storageDir: String,
                                   metaDbPath: String,
                                   metaDbKey: String,
                                   authServiceUrl: String,
                                   defaultQuotaBytes: Long,
                                   auditLogPath: String
                                 )

object FileServerConfig:
  def load(): FileServerConfig = FileServerConfig(
    host             = sys.env.getOrElse("FILE_HOST", "0.0.0.0"),
    port             = sys.env.getOrElse("FILE_PORT", "8082").toInt,
    storageDir       = sys.env.getOrElse("STORAGE_DIR", "./storage"),
    metaDbPath       = sys.env.getOrElse("META_DB_PATH", "meta.enc"),
    metaDbKey        = sys.env.getOrElse("META_DB_KEY", "0123456789abcdef"),
    authServiceUrl   = sys.env.getOrElse("AUTH_SERVICE_URL", "https://localhost:8081"),
    defaultQuotaBytes = sys.env.getOrElse("DEFAULT_QUOTA_BYTES", "104857600").toLong, // 100 MB
    auditLogPath     = sys.env.getOrElse("AUDIT_LOG_PATH", "audit.log")
  )