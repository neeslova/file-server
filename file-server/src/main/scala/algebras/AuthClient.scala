package algebras

import domain.UserInfo

trait AuthClient[F[_]]:
  def validateToken(token: String): F[Option[UserInfo]]
  def listUsers(token: String): F[Option[List[UserInfo]]]