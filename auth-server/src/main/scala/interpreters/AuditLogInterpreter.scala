package interpreters

import cats.effect.IO
import algebras.{AuditLog, AuditEntry}
import java.nio.file.{Files, Paths, StandardOpenOption}
import io.circe.syntax.*
import io.circe.generic.auto.*
import scala.jdk.CollectionConverters.*

class AuditLogInterpreter(logPath: String) extends AuditLog[IO] {

  def log(entry: AuditEntry): IO[Unit] = IO {
    val line = entry.asJson.noSpaces + "\n"
    Files.write(Paths.get(logPath), line.getBytes("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
  }

  def list(limit: Int): IO[List[AuditEntry]] = IO {
    val path = Paths.get(logPath)
    if Files.exists(path) then {
      val lines = Files.readAllLines(path).asScala.toList.takeRight(limit)
      lines.flatMap(line => io.circe.parser.decode[AuditEntry](line).toOption)
    } else Nil
  }
}