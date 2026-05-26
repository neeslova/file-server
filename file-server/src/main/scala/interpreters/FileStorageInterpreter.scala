package interpreters

import cats.effect.IO
import algebras.FileStorage
import config.FileServerConfig
import domain.{FileMeta, FileError, Folder, SharedLink}
import fs2.Stream
import fs2.io.file.{Files => Fs2Files, Path => Fs2Path}
import org.mindrot.jbcrypt.BCrypt
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.nio.file.{Files => JFiles, Paths => JPaths, StandardOpenOption}
import java.time.Instant
import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.Duration
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.generic.auto.*

class FileStorageInterpreter(cfg: FileServerConfig) extends FileStorage[IO] {

  private var meta: TrieMap[UUID, FileMeta] = TrieMap.empty
  private var folders: TrieMap[UUID, Folder] = TrieMap.empty
  private var sharedLinks: TrieMap[String, SharedLink] = TrieMap.empty

  private case class MetaContainer(files: List[FileMeta], folders: List[Folder])

  private def cipher(mode: Int): Cipher = {
    val key = new SecretKeySpec(cfg.metaDbKey.getBytes("UTF-8"), "AES")
    val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
    c.init(mode, key)
    c
  }

  private def load(): MetaContainer = {
    val path = JPaths.get(cfg.metaDbPath)
    if JFiles.exists(path) then {
      val encrypted = JFiles.readAllBytes(path)
      val decrypted = cipher(Cipher.DECRYPT_MODE).doFinal(encrypted)
      val json = new String(decrypted, "UTF-8")
      decode[MetaContainer](json).getOrElse(MetaContainer(Nil, Nil))
    } else MetaContainer(Nil, Nil)
  }

  private def save(): Unit = {
    val container = MetaContainer(meta.values.toList, folders.values.toList)
    val json = container.asJson.noSpaces
    val bytes = json.getBytes("UTF-8")
    val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(bytes)
    JFiles.write(JPaths.get(cfg.metaDbPath), encrypted, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  locally {
    val container = load()
    meta = TrieMap.from(container.files.map(f => f.id -> f))
    folders = TrieMap.from(container.folders.map(f => f.id -> f))
  }

  def uploadFile(userId: UUID, fileName: String, parentFolderId: Option[UUID], password: String, uploadedByLogin: String, data: Stream[IO, Byte]): IO[Either[FileError, FileMeta]] = {
    val id = UUID.randomUUID
    val filePath = Fs2Path.fromNioPath(JPaths.get(cfg.storageDir, id.toString))
    JFiles.createDirectories(JPaths.get(cfg.storageDir))
    data.through(Fs2Files[IO].writeAll(filePath))
      .compile
      .toList
      .map { _ =>
        val fileSize = JFiles.size(JPaths.get(cfg.storageDir, id.toString))
        val fileMeta = FileMeta(id, fileName, fileSize, BCrypt.hashpw(password, BCrypt.gensalt()), userId, uploadedByLogin, Instant.now, parentFolderId)
        meta += (id -> fileMeta)
        save()
        Right(fileMeta)
      }
  }

  def downloadFile(fileId: UUID, password: String): IO[Either[FileError, (FileMeta, Stream[IO, Byte])]] = IO {
    meta.get(fileId) match {
      case None => Left(FileError.FileNotFound)
      case Some(m) if !BCrypt.checkpw(password, m.passwordHash) => Left(FileError.WrongPassword)
      case Some(m) =>
        val path = Fs2Path.fromNioPath(JPaths.get(cfg.storageDir, fileId.toString))
        Right((m, Fs2Files[IO].readAll(path)))
    }
  }

  def getFileMeta(fileId: UUID): IO[Option[FileMeta]] = IO(meta.get(fileId))

  def deleteFile(fileId: UUID, userId: UUID): IO[Either[FileError, Unit]] = IO {
    meta.get(fileId) match {
      case None => Left(FileError.FileNotFound)
      case Some(m) if m.uploadedBy != userId => Left(FileError.Unauthorized)
      case Some(_) =>
        meta -= fileId
        save()
        JFiles.deleteIfExists(JPaths.get(cfg.storageDir, fileId.toString))
        Right(())
    }
  }

  def adminDeleteFile(fileId: UUID): IO[Either[FileError, Unit]] = IO {
    meta.get(fileId) match {
      case None => Left(FileError.FileNotFound)
      case Some(_) =>
        meta -= fileId
        save()
        JFiles.deleteIfExists(JPaths.get(cfg.storageDir, fileId.toString))
        Right(())
    }
  }

  def listFiles(userId: UUID, folderId: Option[UUID]): IO[List[FileMeta]] = IO(
    meta.values.toList.sortBy(_.uploadedAt)
  )

  def createFolder(userId: UUID, name: String, parentId: Option[UUID]): IO[Either[FileError, Folder]] = IO {
    val folder = Folder(UUID.randomUUID, name, userId, parentId)
    folders += (folder.id -> folder)
    save()
    Right(folder)
  }

  def searchFiles(userId: UUID, query: String): IO[List[FileMeta]] = IO(
    meta.values.filter(f => f.originalName.toLowerCase.contains(query.toLowerCase)).toList
  )

  def createShareLink(fileId: UUID, userId: UUID, expiresIn: Duration): IO[Either[FileError, SharedLink]] = IO {
    meta.get(fileId) match {
      case None => Left(FileError.FileNotFound)
      case Some(m) if m.uploadedBy != userId => Left(FileError.Unauthorized)
      case Some(_) =>
        val token = UUID.randomUUID.toString
        val link = SharedLink(token, fileId, Instant.now, Instant.now.plusSeconds(expiresIn.toSeconds))
        sharedLinks += (token -> link)
        Right(link)
    }
  }

  def getShareLink(token: String): IO[Option[SharedLink]] = IO(sharedLinks.get(token))
  def deleteShareLink(token: String): IO[Unit] = IO(sharedLinks -= token)

  def getUserUsage(userId: UUID): IO[Long] = IO(meta.values.filter(_.uploadedBy == userId).map(_.size).sum)
  def getUserQuota(userId: UUID): IO[Long] = IO.pure(cfg.defaultQuotaBytes)
}