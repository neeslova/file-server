package algebras

import domain.{FileMeta, FileError, Folder, SharedLink}
import fs2.Stream
import java.util.UUID
import scala.concurrent.duration.Duration

trait FileStorage[F[_]]:
  def uploadFile(userId: UUID, fileName: String, parentFolderId: Option[UUID], password: String, uploadedByLogin: String, data: Stream[F, Byte]): F[Either[FileError, FileMeta]]
  def downloadFile(fileId: UUID, password: String): F[Either[FileError, (FileMeta, Stream[F, Byte])]]
  def getFileMeta(fileId: UUID): F[Option[FileMeta]]
  def deleteFile(fileId: UUID, userId: UUID): F[Either[FileError, Unit]]
  def adminDeleteFile(fileId: UUID): F[Either[FileError, Unit]]
  def listFiles(userId: UUID, folderId: Option[UUID]): F[List[FileMeta]]
  def createFolder(userId: UUID, name: String, parentId: Option[UUID]): F[Either[FileError, Folder]]
  def searchFiles(userId: UUID, query: String): F[List[FileMeta]]
  def createShareLink(fileId: UUID, userId: UUID, expiresIn: Duration): F[Either[FileError, SharedLink]]
  def getShareLink(token: String): F[Option[SharedLink]]
  def deleteShareLink(token: String): F[Unit]
  def getUserUsage(userId: UUID): F[Long]
  def getUserQuota(userId: UUID): F[Long]