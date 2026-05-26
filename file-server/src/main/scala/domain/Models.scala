package domain

import java.util.UUID
import java.time.Instant

final case class FileMeta(
                           id: UUID,
                           originalName: String,
                           size: Long,
                           passwordHash: String,
                           uploadedBy: UUID,
                           uploadedByLogin: String,
                           uploadedAt: Instant,
                           parentFolderId: Option[UUID] = None
                         )

final case class Folder(
                         id: UUID,
                         name: String,
                         ownerId: UUID,
                         parentFolderId: Option[UUID]
                       )

final case class SharedLink(
                             token: String,
                             fileId: UUID,
                             createdAt: Instant,
                             expiresAt: Instant
                           )

final case class UserInfo(id: UUID, login: String, role: String)