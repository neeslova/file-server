package algebras

import domain.{LoginRequest, RegisterRequest, TokenPair, UserInfo, AuthError}
import java.util.UUID

trait Auth[F[_]]:
  def register(req: RegisterRequest): F[Either[AuthError, UserInfo]]
  def login(req: LoginRequest): F[Either[AuthError, TokenPair]]
  def validateToken(token: String): F[Either[AuthError, UserInfo]]
  def refreshToken(refreshToken: String): F[Either[AuthError, String]]
  def listUsers(adminId: UUID): F[Either[AuthError, List[UserInfo]]]
  def deleteUser(adminId: UUID, userId: UUID): F[Either[AuthError, Unit]]
  def changePassword(adminId: UUID, userId: UUID, newPassword: String): F[Either[AuthError, Unit]]