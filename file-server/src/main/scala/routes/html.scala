package routes

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`
import domain.{FileMeta, UserInfo}
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.UUID

object html {

  private def page(title: String, body: String, isAdmin: Boolean = false, scripts: String = ""): IO[Response[IO]] = {
    val adminLink = if (isAdmin) """<a class="nav-link" href="/admin">Админка</a>""" else ""
    val htmlStr = s"""<!DOCTYPE html><html><head><title>$title</title>
      <meta charset="UTF-8">
      <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
      <style>
        body{background-color:#f8f9fa;padding:20px}
        .navbar{margin-bottom:20px}
        .card{margin-bottom:20px}
        .table td, .table th{vertical-align:middle}
        .btn-action{margin-right:5px}
      </style>
      </head><body>
      <nav class="navbar navbar-expand-lg navbar-dark bg-dark">
        <div class="container-fluid">
          <a class="navbar-brand" href="/files">Файловый сервер</a>
          <div class="navbar-nav">
            <a class="nav-link" href="/files">Файлы</a>
            <a class="nav-link" href="/upload">Загрузить</a>
            <a class="nav-link" href="/search">Поиск</a>
            $adminLink
          </div>
          <div class="navbar-nav ms-auto">
            <a class="nav-link" href="/logout">Выйти</a>
          </div>
        </div>
      </nav>
      <div class="container">
        $body
      </div>
      <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
      $scripts
      </body></html>"""
    Ok(htmlStr).map(_.withContentType(`Content-Type`(MediaType.text.html)))
  }

  def filesPage(files: List[FileMeta], currentUserId: UUID, isAdmin: Boolean): IO[Response[IO]] = {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    val fileItems = files.map { f =>
      val deleteButton = if (f.uploadedBy == currentUserId)
        s"<a href='/delete/${f.id}' class='btn btn-sm btn-danger btn-action'>Удалить</a>"
      else ""
      s"""<tr>
        <td>📄 ${f.originalName}</td>
        <td>${f.size / 1024} KB</td>
        <td>${f.uploadedByLogin}</td>
        <td>${f.uploadedAt.atZone(ZoneId.systemDefault()).format(formatter)}</td>
        <td>
          <a href='/download/${f.id}' class='btn btn-sm btn-primary btn-action'>Скачать</a>
          $deleteButton
        </td>
      </tr>"""
    }.mkString
    page("Файлы",
      s"""<h2>Список файлов</h2>
      <table class="table table-striped">
        <thead><tr><th>Имя</th><th>Размер</th><th>Загрузил</th><th>Время загрузки</th><th>Действия</th></tr></thead>
        <tbody>$fileItems</tbody>
      </table>""",
      isAdmin = isAdmin)
  }

  def uploadPage(error: Option[String]): IO[Response[IO]] = {
    val msg = error.map(e => s"<div class='alert alert-danger'>$e</div>").getOrElse("")
    page("Загрузка",
      s"""<h2>Загрузить файл</h2>$msg
      <form method="post" enctype="multipart/form-data">
        <div class="mb-3">
          <input class="form-control" name="file" type="file" required>
        </div>
        <div class="mb-3">
          <input class="form-control" name="password" type="password" placeholder="Пароль файла" required>
        </div>
        <button class="btn btn-primary">Загрузить</button>
      </form>
      <a href="/files" class="btn btn-secondary mt-2">Назад к файлам</a>""")
  }

  def downloadPage(file: FileMeta, error: Option[String]): IO[Response[IO]] = {
    val msg = error.map(e => s"<div class='alert alert-danger'>$e</div>").getOrElse("")
    val name = if (file != null) file.originalName else "неизвестно"
    val size = if (file != null) s"<p>Размер: ${file.size / 1024} KB</p>" else ""
    page("Скачивание",
      s"""<h2>Скачать: $name</h2>$size$msg
      <form method="post" action="/download/${if (file != null) file.id else ""}">
        <div class="mb-3">
          <input class="form-control" name="password" type="password" placeholder="Пароль файла" required>
        </div>
        <button class="btn btn-primary">Скачать</button>
      </form>
      <a href="/files" class="btn btn-secondary mt-2">Назад к файлам</a>""")
  }

  def deletePage(file: FileMeta): IO[Response[IO]] = {
    page("Удаление файла",
      s"""<h2>Удалить: ${file.originalName}</h2>
      <p>Вы уверены? Это действие нельзя отменить.</p>
      <form method="post" action="/delete/${file.id}">
        <div class="mb-3">
          <input class="form-control" name="password" type="password" placeholder="Пароль файла" required>
        </div>
        <button class="btn btn-danger">Удалить</button>
      </form>
      <a href="/files" class="btn btn-secondary mt-2">Отмена</a>""")
  }

  def searchPage(results: List[FileMeta], query: String): IO[Response[IO]] = {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    val items = results.map(f =>
      s"""<tr>
        <td>📄 ${f.originalName}</td>
        <td>${f.size / 1024} KB</td>
        <td>${f.uploadedByLogin}</td>
        <td>${f.uploadedAt.atZone(ZoneId.systemDefault()).format(formatter)}</td>
        <td><a href='/download/${f.id}' class='btn btn-sm btn-primary'>Скачать</a></td>
      </tr>"""
    ).mkString
    page("Поиск",
      s"""<h2>Поиск файлов</h2>
      <form method="get" action="/search" class="row g-3">
        <div class="col-auto">
          <input class="form-control" name="q" value="$query" placeholder="Поиск...">
        </div>
        <div class="col-auto">
          <button class="btn btn-primary">Искать</button>
        </div>
      </form>
      <table class="table table-striped mt-3">
        <thead><tr><th>Имя</th><th>Размер</th><th>Загрузил</th><th>Время загрузки</th><th></th></tr></thead>
        <tbody>$items</tbody>
      </table>
      <a href="/files" class="btn btn-secondary mt-2">Назад к файлам</a>""")
  }

  def adminPage(users: List[UserInfo], files: List[FileMeta], logs: List[String]): IO[Response[IO]] = {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    val userItems = users.map { u =>
      s"""<tr>
        <td>${u.login}</td>
        <td>${u.role}</td>
        <td>
          <button class="btn btn-sm btn-warning" onclick="changePassword('${u.id}')">Сменить пароль</button>
          <button class="btn btn-sm btn-danger" onclick="deleteUser('${u.id}')">Удалить</button>
        </td>
      </tr>"""
    }.mkString

    val fileItems = files.map { f =>
      s"""<tr>
        <td>${f.originalName}</td>
        <td>${f.uploadedByLogin}</td>
        <td>${f.uploadedAt.atZone(ZoneId.systemDefault()).format(formatter)}</td>
        <td>
          <a href='/download/${f.id}' class='btn btn-sm btn-primary'>Скачать</a>
          <button class="btn btn-sm btn-danger" onclick="deleteFile('${f.id}')">Удалить</button>
        </td>
      </tr>"""
    }.mkString

    val logItems = logs.map { l =>
      s"""<tr><td>$l</td></tr>"""
    }.mkString

    page("Админка",
      s"""<h2>Административная панель</h2>

      <div class="card">
        <div class="card-header"><h5>Пользователи</h5></div>
        <div class="card-body">
          <table class="table">
            <thead><tr><th>Логин</th><th>Роль</th><th>Действия</th></tr></thead>
            <tbody>$userItems</tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <div class="card-header"><h5>Все файлы</h5></div>
        <div class="card-body">
          <table class="table">
            <thead><tr><th>Имя</th><th>Владелец</th><th>Загружен</th><th>Действия</th></tr></thead>
            <tbody>$fileItems</tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <div class="card-header"><h5>Логи</h5></div>
        <div class="card-body">
          <table class="table">
            <thead><tr><th>Событие</th></tr></thead>
            <tbody>$logItems</tbody>
          </table>
        </div>
      </div>
      """,
      scripts = """
      <script>
      function changePassword(userId) {
        const newPassword = prompt('Введите новый пароль:');
        if (newPassword) {
          fetch('https://localhost:8081/admin/users/' + userId + '/change-password', {
            method: 'POST',
            headers: {'Authorization': 'Bearer ' + getCookie('jwt')},
            body: new URLSearchParams({password: newPassword})
          }).then(r => { if (r.ok) alert('Пароль изменён'); else alert('Ошибка'); });
        }
      }
      function deleteUser(userId) {
        if (confirm('Удалить пользователя?')) {
          fetch('https://localhost:8081/admin/users/' + userId + '/delete', {
            method: 'POST',
            headers: {'Authorization': 'Bearer ' + getCookie('jwt')}
          }).then(r => { if (r.ok) alert('Пользователь удалён'); else alert('Ошибка'); });
        }
      }
      function deleteFile(fileId) {
        if (confirm('Удалить файл?')) {
          fetch('/files/' + fileId, {method: 'DELETE'}).then(() => location.reload());
        }
      }
      function getCookie(name) {
        const value = `; ${document.cookie}`;
        const parts = value.split(`; ${name}=`);
        if (parts.length === 2) return parts.pop().split(';').shift();
      }
      </script>
      """
    )
  }
}