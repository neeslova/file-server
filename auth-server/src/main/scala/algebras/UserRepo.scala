package algebras

import domain.User
import java.util.UUID

trait UserRepo[F[_]]:
  def findByLogin(login: String): F[Option[User]]
  def findById(id: UUID): F[Option[User]]
  def create(user: User): F[Unit]
  def update(user: User): F[Unit]
  def delete(id: UUID): F[Unit]
  def list: F[List[User]]