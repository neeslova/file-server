# Файловый сервер с аутентификацией

Два независимых микросервиса на Scala 3 с Tagless Final:
- **Auth Server** – аутентификация, JWT, управление пользователями
- **File Server** – загрузка/скачивание файлов, парольная защита, админка

## Стек технологий

| Компонент | Технология |
|-----------|------------|
| HTTP-сервер | http4s + Ember |
| Эффекты | Cats Effect IO |
| Стриминг файлов | fs2 |
| JSON | Circe |
| Аутентификация | JWT (jwt-scala) |
| Пароли | bcrypt |
| Шифрование хранилища | AES-128-ECB |
| HTTPS | TLS (сертификат PKCS12) |
| Фронтенд | Bootstrap 5 (server-side rendering) |


Каждый сервер имеет собственный `build.sbt` и запускается отдельно.

## Запуск

### Требования
- Java 17+
- sbt 1.12+
- Сгенерированный сертификат `keystore.p12` в корне каждого модуля (или используется самоподписанный)

### Генерация сертификата (один раз)

keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -storepass 123456 -validity 365 -dname "CN=localhost"
Скопировать keystore.p12 в папки auth-server/ и file-server/.

Запуск Auth Server (порт 8081)
bash
cd auth-server
sbt run
Запуск File Server (порт 8082)
bash
cd file-server
sbt run
Открыть в браузере: https://localhost:8082

Переменные окружения (опционально)
Переменная	По умолчанию	Описание
AUTH_HOST	0.0.0.0	Хост Auth Server
AUTH_PORT	8081	Порт Auth Server
FILE_HOST	0.0.0.0	Хост File Server
FILE_PORT	8082	Порт File Server
JWT_SECRET	change-me	Секрет для подписи JWT
USER_DB_KEY	0123456789abcdef	Ключ шифрования users.enc
META_DB_KEY	0123456789abcdef	Ключ шифрования meta.enc
STORAGE_DIR	./storage	Папка для хранения файлов
AUTH_SERVICE_URL	https://localhost:8081	URL Auth Server для File Server
Роли и доступ
user – может загружать файлы, скачивать любые файлы (по паролю), удалять только свои файлы

admin – дополнительно: просмотр всех файлов, удаление любых файлов без пароля, управление пользователями (смена пароля, удаление)

При старте Auth Server пользователь с логином admin автоматически получает роль admin. Пароль задаётся при регистрации.

Безопасность
HTTPS – все соединения шифруются (самоподписанный сертификат для разработки)

Пароли – хешируются bcrypt (пользователи и файлы)

JWT – токены доступа (15 мин) и обновления (7 дней), передаются в HttpOnly куках

Хранилище – файлы users.enc и meta.enc шифруются AES-128-ECB, ключи задаются через переменные окружения

Пароли файлов – при скачивании и удалении требуется пароль, заданный при загрузке

Взаимодействие микросервисов
Браузер → File Server (8082)

File Server → Auth Server (8081) для проверки JWT (POST /validate)

Auth Server возвращает информацию о пользователе (id, login, role)

API (основные эндпоинты)
Auth Server
GET /login, GET /register – страницы входа/регистрации

POST /login, POST /register – обработка форм

POST /validate – проверка JWT (для File Server)

GET /admin/users – список пользователей (admin)

POST /admin/users/{id}/delete – удалить пользователя (admin)

POST /admin/users/{id}/change-password – сменить пароль пользователя (admin)

File Server
GET /files – список файлов

GET /upload – страница загрузки

POST /upload – загрузка файла (multipart)

GET /download/{id} – страница скачивания

POST /download/{id} – скачать файл (с паролем)

GET /delete/{id} – страница удаления

POST /delete/{id} – удалить файл (с паролем)

GET /search – поиск файлов

POST /share/{id} – создать ссылку общего доступа

GET /shared/{token} – скачать по ссылке

DELETE /files/{id} – удалить файл без пароля (admin)

GET /admin – админ-панель (admin)