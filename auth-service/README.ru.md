[English](./README.md) | **Русский**

# Auth Service

Владелец пользователей, сессий, opaque access/refresh токенов, данных
профиля и ролей.

## Зоны ответственности

- Регистрация, login, logout, refresh.
- Интроспекция токенов на **внутреннем** пути
  `POST /internal/auth/introspect` (вызывается Gateway, наружу не
  выставлен).
- Чтение/обновление профиля.
- Управление ролями.

Токены -- непрозрачные случайные строки в таблице `sessions`; нет JWT
и нет ключа подписи. Обоснование -- в
[`docs/architecture.ru.md`](../docs/architecture.ru.md#аутентификация-и-авторизация).

## Эндпоинты

| Метод  | Путь                              | Auth      | Заметки                              |
|--------|-----------------------------------|-----------|--------------------------------------|
| POST   | `/api/v1/auth/register`           | public    |                                      |
| POST   | `/api/v1/auth/login`              | public    | Возвращает access + refresh          |
| POST   | `/api/v1/auth/refresh`            | public    | Ротирует refresh-токен               |
| POST   | `/api/v1/auth/logout`             | bearer    |                                      |
| GET    | `/api/v1/profile/me`              | bearer    |                                      |
| PATCH  | `/api/v1/profile/me`              | bearer    |                                      |
| POST   | `/api/v1/profile/me/roles`        | admin     |                                      |
| POST   | `/internal/auth/introspect`       | internal  | Не маршрутизируется через Gateway    |

Полная спецификация: http://localhost:8081/swagger-ui.html.

## Переменные окружения

| Имя              | Default          | Описание                        |
|------------------|------------------|---------------------------------|
| `SERVER_PORT`    | `8081`           | HTTP-порт                       |
| `DB_HOST`        | `localhost`      | Хост PostgreSQL                 |
| `DB_NAME`        | `hvostid_auth`   | Имя базы                        |
| `DB_USER`        | `hvostid`        | Пользователь БД                 |
| `DB_PASSWORD`    | `hvostid`        | Пароль БД                       |

TTL токенов (`hvostid.auth.access-token-ttl`,
`hvostid.auth.refresh-token-ttl`) настраиваются в
[`application.yml`](./src/main/resources/application.yml).
Production-override -- в
[`application-prod.yml`](./src/main/resources/application-prod.yml).

## Локальный запуск

```bash
docker compose up -d postgres
./gradlew :auth-service:bootRun
```

## Зависимости

- **Обязательно:** PostgreSQL (база `hvostid_auth`).
- **Другие сервисы не вызывают этот сервис снаружи** -- только Gateway
  ходит во внутренний introspect-эндпоинт.
