package interpreters

import cats.effect.IO
import config.AuthServerConfig
import algebras.{Auth, UserRepo, AuditLog, AuditEntry}
import domain.{User, LoginRequest, RegisterRequest, TokenPair, UserInfo, AuthError}
import pdi.jwt.{JwtCirce, JwtAlgorithm, JwtClaim}
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import java.time.Instant

class AuthInterpreter(cfg: AuthServerConfig, userRepo: UserRepo[IO], auditLog: AuditLog[IO]) extends Auth[IO] {

  private val accessTokenDuration = 900L  // 15 minutes
  private val refreshTokenDuration = 604800L // 7 days

  def register(req: RegisterRequest): IO[Either[AuthError, UserInfo]] = {
    userRepo.findByLogin(req.login).flatMap {
      case Some(_) => IO.pure(Left(AuthError.UserAlreadyExists))
      case None =>
        val user = User(
          id = UUID.randomUUID,
          login = req.login,
          password = BCrypt.hashpw(req.password, BCrypt.gensalt()),
          role = "user",
          createdAt = Instant.now,
          blocked = false,
          blockedUntil = None
        )
        userRepo.create(user).flatMap { _ =>
          auditLog.log(AuditEntry(Instant.now, "REGISTER", Some(user.id.toString), None, s"User ${user.login} registered"))
            .map(_ => Right(UserInfo(user.id, user.login, user.role)))
        }
    }
  }

  def login(req: LoginRequest): IO[Either[AuthError, TokenPair]] = {
    userRepo.findByLogin(req.login).flatMap {
      case None => IO.pure(Left(AuthError.InvalidCredentials))
      case Some(user) if user.blocked && user.blockedUntil.exists(_.isAfter(Instant.now)) =>
        IO.pure(Left(AuthError.UserBlocked))
      case Some(user) if !BCrypt.checkpw(req.password, user.password) =>
        IO.pure(Left(AuthError.InvalidCredentials))
      case Some(user) =>
        val accessToken = generateToken(user, accessTokenDuration)
        val refreshToken = generateToken(user, refreshTokenDuration)
        auditLog.log(AuditEntry(Instant.now, "LOGIN", Some(user.id.toString), None, s"User ${user.login} logged in"))
          .map(_ => Right(TokenPair(accessToken, refreshToken)))
    }
  }

  def validateToken(token: String): IO[Either[AuthError, UserInfo]] = {
    IO {
      JwtCirce.decode(token, cfg.jwtSecret, Seq(JwtAlgorithm.HS256)) match {
        case util.Success(claim) => Right(claim.subject.getOrElse(""))
        case util.Failure(_)    => Left(AuthError.InvalidToken)
      }
    }.flatMap {
      case Left(error) => IO.pure(Left(error))
      case Right(userIdStr) if userIdStr.nonEmpty =>
        val userId = UUID.fromString(userIdStr)
        userRepo.findById(userId).map {
          case Some(user) => Right(UserInfo(user.id, user.login, user.role))
          case None       => Left(AuthError.UserNotFound)
        }
      case _ => IO.pure(Left(AuthError.InvalidToken))
    }
  }

  def refreshToken(refreshToken: String): IO[Either[AuthError, String]] = {
    JwtCirce.decode(refreshToken, cfg.jwtSecret, Seq(JwtAlgorithm.HS256)) match {
      case util.Success(claim) =>
        val userId = UUID.fromString(claim.subject.getOrElse(""))
        userRepo.findById(userId).map {
          case Some(user) => Right(generateToken(user, accessTokenDuration))
          case None       => Left(AuthError.UserNotFound)
        }
      case util.Failure(_) => IO.pure(Left(AuthError.InvalidToken))
    }
  }

  def listUsers(adminId: UUID): IO[Either[AuthError, List[UserInfo]]] = {
    userRepo.findById(adminId).flatMap {
      case Some(admin) if admin.role == "admin" =>
        userRepo.list.map(users => Right(users.map(u => UserInfo(u.id, u.login, u.role))))
      case _ => IO.pure(Left(AuthError.Unauthorized))
    }
  }

  def deleteUser(adminId: UUID, userId: UUID): IO[Either[AuthError, Unit]] = {
    userRepo.findById(adminId).flatMap {
      case Some(admin) if admin.role == "admin" =>
        userRepo.findById(userId).flatMap {
          case Some(user) =>
            userRepo.delete(userId).flatMap { _ =>
              auditLog.log(AuditEntry(Instant.now, "DELETE_USER", Some(adminId.toString), None, s"User ${user.login} deleted by admin"))
                .map(_ => Right(()))
            }
          case None => IO.pure(Left(AuthError.UserNotFound))
        }
      case _ => IO.pure(Left(AuthError.Unauthorized))
    }
  }

  def changePassword(adminId: UUID, userId: UUID, newPassword: String): IO[Either[AuthError, Unit]] = {
    userRepo.findById(adminId).flatMap {
      case Some(admin) if admin.role == "admin" =>
        userRepo.findById(userId).flatMap {
          case Some(user) =>
            val updatedUser = user.copy(password = BCrypt.hashpw(newPassword, BCrypt.gensalt()))
            userRepo.update(updatedUser).flatMap { _ =>
              auditLog.log(AuditEntry(Instant.now, "CHANGE_PASSWORD", Some(adminId.toString), None, s"Password changed for user ${user.login} by admin"))
                .map(_ => Right(()))
            }
          case None => IO.pure(Left(AuthError.UserNotFound))
        }
      case _ => IO.pure(Left(AuthError.Unauthorized))
    }
  }

  private def generateToken(user: User, durationSeconds: Long): String = {
    val claim = JwtClaim(
      subject = Some(user.id.toString),
      expiration = Some(Instant.now.plusSeconds(durationSeconds).getEpochSecond)
    )
    JwtCirce.encode(claim, cfg.jwtSecret, JwtAlgorithm.HS256)
  }
}