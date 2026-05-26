package interpreters

import cats.effect.IO
import config.AuthServerConfig
import algebras.UserRepo
import domain.User
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.UUID
import scala.collection.concurrent.TrieMap
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.generic.auto.*

class UserRepoInterpreter(cfg: AuthServerConfig) extends UserRepo[IO] {

  private val users: TrieMap[UUID, User] = load()

  private def cipher(mode: Int): Cipher = {
    val key = new SecretKeySpec(cfg.userDbKey.getBytes("UTF-8"), "AES")
    val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
    c.init(mode, key)
    c
  }

  private def load(): TrieMap[UUID, User] = {
    val path = Paths.get(cfg.userDbPath)
    if Files.exists(path) then {
      val encrypted = Files.readAllBytes(path)
      val decrypted = cipher(Cipher.DECRYPT_MODE).doFinal(encrypted)
      val json = new String(decrypted, "UTF-8")
      val userList = decode[List[User]](json).getOrElse(Nil)
      TrieMap.from(userList.map(u => u.id -> u))
    } else TrieMap.empty
  }

  private def save(): Unit = {
    val userList = users.values.toList
    val json = userList.asJson.noSpaces
    val bytes = json.getBytes("UTF-8")
    val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(bytes)
    Files.write(Paths.get(cfg.userDbPath), encrypted, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  def findByLogin(login: String): IO[Option[User]] =
    IO(users.values.find(_.login == login))

  def findById(id: UUID): IO[Option[User]] =
    IO(users.get(id))

  def create(user: User): IO[Unit] = IO {
    users += (user.id -> user)
    save()
  }

  def update(user: User): IO[Unit] = IO {
    users += (user.id -> user)
    save()
  }

  def delete(id: UUID): IO[Unit] = IO {
    users -= id
    save()
  }

  def list: IO[List[User]] = IO(users.values.toList)
}