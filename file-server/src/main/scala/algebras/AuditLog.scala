package algebras

import java.time.Instant

final case class AuditEntry(
                             timestamp: Instant,
                             event: String,
                             userId: Option[String],
                             ip: Option[String],
                             details: String
                           )

trait AuditLog[F[_]]:
  def log(entry: AuditEntry): F[Unit]
  def list(limit: Int): F[List[AuditEntry]]