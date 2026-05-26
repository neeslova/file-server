package routes

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`

object html {

  private def page(title: String, body: String): IO[Response[IO]] = {
    val htmlStr = s"""<!DOCTYPE html><html><head><title>$title</title>
      <meta charset="UTF-8">
      <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
      <style>
        body{background-color:#f8f9fa;padding:20px}
        .navbar{margin-bottom:20px}
        .card{margin-bottom:20px}
        .auth-form{max-width:400px;margin:0 auto}
      </style>
      </head><body>
      <nav class="navbar navbar-expand-lg navbar-dark bg-dark">
        <div class="container-fluid">
          <a class="navbar-brand" href="/login">Аутентификация</a>
        </div>
      </nav>
      <div class="container">
        $body
      </div>
      <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
      </body></html>"""
    Ok(htmlStr).map(_.withContentType(`Content-Type`(MediaType.text.html)))
  }

  def loginPage(error: Option[String]): IO[Response[IO]] = {
    val msg = error.map(e => s"<div class='alert alert-danger'>$e</div>").getOrElse("")
    page("Вход",
      s"""<div class="auth-form">
        <h2 class="mb-3">Вход</h2>$msg
        <form method="post">
          <div class="mb-3">
            <input class="form-control" name="login" placeholder="Логин" required>
          </div>
          <div class="mb-3">
            <input class="form-control" name="password" type="password" placeholder="Пароль" required>
          </div>
          <button class="btn btn-primary w-100">Войти</button>
        </form>
        <p class="mt-2"><a href="/register">Зарегистрироваться</a></p>
      </div>""")
  }

  def registerPage(error: Option[String]): IO[Response[IO]] = {
    val msg = error.map(e => s"<div class='alert alert-danger'>$e</div>").getOrElse("")
    page("Регистрация",
      s"""<div class="auth-form">
        <h2 class="mb-3">Регистрация</h2>$msg
        <form method="post">
          <div class="mb-3">
            <input class="form-control" name="login" placeholder="Логин" required>
          </div>
          <div class="mb-3">
            <input class="form-control" name="password" type="password" placeholder="Пароль" required>
          </div>
          <button class="btn btn-primary w-100">Зарегистрироваться</button>
        </form>
        <p class="mt-2"><a href="/login">Войти</a></p>
      </div>""")
  }
}