package domain

enum AuthError {
  case InvalidCredentials
  case UserAlreadyExists
  case UserNotFound
  case TokenExpired
  case InvalidToken
  case UserBlocked
  case Unauthorized
  case InternalError(msg: String)

  def message: String = this match {
    case InvalidCredentials => "Invalid login or password"
    case UserAlreadyExists => "User already exists"
    case UserNotFound      => "User not found"
    case TokenExpired      => "Token expired"
    case InvalidToken      => "Invalid token"
    case UserBlocked       => "User is blocked"
    case Unauthorized      => "Admin access required"
    case InternalError(m)  => m
  }
}