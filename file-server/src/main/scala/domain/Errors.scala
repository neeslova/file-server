package domain

enum FileError {
  case FileNotFound
  case WrongPassword
  case Unauthorized
  case QuotaExceeded
  case InternalError(msg: String)

  def message: String = this match {
    case FileNotFound     => "File not found"
    case WrongPassword    => "Wrong file password"
    case Unauthorized     => "Please log in"
    case QuotaExceeded    => "Storage quota exceeded"
    case InternalError(m) => m
  }
}