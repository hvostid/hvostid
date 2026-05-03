[English](./README.md) | **Русский**

# API Gateway

Spring Cloud Gateway (server-MVC) -- парадная дверь платформы.

## Зоны ответственности

- Маршрутизирует трафик `/api/v1/**` в нужный сервис.
- Валидирует входящие Bearer-токены через интроспекцию Auth Service и
  проставляет `X-User-Id` / `X-User-Roles` для downstream-сервисов
  (`TokenIntrospectionFilter`).
- Генерирует / прокидывает `X-Request-Id` и привязывает его к MDC для
  корреляции логов (`RequestIdFilter`).
- Token-bucket rate limit по IP клиента (`RateLimitFilter`).

Полный жизненный цикл запроса описан в
[`docs/architecture.ru.md`](../docs/architecture.ru.md#жизненный-цикл-запроса).

## Маршрутизация

| Префикс пути           | Целевой сервис              |
|------------------------|-----------------------------|
| `/api/v1/auth/**`      | Auth Service (`:8081`)      |
| `/api/v1/profile/**`   | Auth Service (`:8081`)      |
| `/api/v1/listings/**`  | Listing Service (`:8082`)   |
| `/api/v1/passports/**` | Passport Service (`:8083`)  |
| `/api/v1/match/**`     | Matching Service (`:8084`)  |

Public paths (без токена): `/api/v1/auth/login`,
`/api/v1/auth/register`, `/actuator/**`.

## Переменные окружения

| Имя                       | Default     | Описание                          |
|---------------------------|-------------|-----------------------------------|
| `SERVER_PORT`             | `8080`      | HTTP-порт                         |
| `AUTH_SERVICE_HOST`       | `localhost` | Hostname Auth Service             |
| `LISTING_SERVICE_HOST`    | `localhost` | Hostname Listing Service          |
| `PASSPORT_SERVICE_HOST`   | `localhost` | Hostname Passport Service         |
| `MATCHING_SERVICE_HOST`   | `localhost` | Hostname Matching Service         |

Rate limit (`hvostid.rate-limit.replenish-rate` / `burst-capacity`) и
auth (`hvostid.auth.introspect-url` / `introspect-timeout`)
настраиваются в
[`application.yml`](./src/main/resources/application.yml).

## Локальный запуск

Поднимите зависимости, затем запустите gateway из IDE или:

```bash
docker compose up -d postgres minio minio-init auth-service listing-service passport-service matching-service
./gradlew :api-gateway:bootRun
```

## Зависимости

- **Обязательно в рантайме:** Auth Service (введение).
- **Обязательно для проксирования трафика:** тот downstream-сервис, на
  который ведёт текущий путь.
