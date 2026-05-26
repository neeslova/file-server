package domain

import java.util.UUID
import java.time.Instant

final case class User(
                       id: UUID,
                       login: String,
                       password: String,
                       role: String,
                       createdAt: Instant,
                       blocked: Boolean,
                       blockedUntil: Option[Instant]
                     )

final case class LoginRequest(login: String, password: String)

final case class RegisterRequest(login: String, password: String)

final case class TokenPair(accessToken: String, refreshToken: String)

final case class UserInfo(id: UUID, login: String, role: String)