[English](./README.md) | **Русский**

# Matching Service

Владеет анкетой покупателя и оценкой совместимости владельца и
питомца.

## Зоны ответственности

- CRUD анкеты покупателя с upsert-семантикой (одна анкета на
  покупателя; повторный submit перезаписывает).
- Расчёт оценки совместимости через **RestClient** к Listing Service и
  Passport Service (internal read). В ответе могут быть `degraded` и
  `degradedReason` при неполных данных паспорта.
- Возврат отсортированных рекомендаций покупателю.

## Эндпоинты

| Метод  | Путь                                   | Auth   | Заметки                          |
|--------|----------------------------------------|--------|----------------------------------|
| GET    | `/api/v1/match/questionnaire`          | buyer  | Прочитать ответы текущего покупателя |
| PUT    | `/api/v1/match/questionnaire`          | buyer  | Upsert                           |
| DELETE | `/api/v1/match/questionnaire`          | buyer  |                                  |
| GET    | `/api/v1/match/recommendations`        | buyer  | Отсортированные объявления       |
| POST   | `/api/v1/match/score`                  | buyer  | Оценка для одного объявления     |

Полная спецификация: http://localhost:8084/swagger-ui.html.

## Переменные окружения

| Имя                     | Default             | Описание              |
|-------------------------|---------------------|-----------------------|
| `SERVER_PORT`           | `8084`              | HTTP-порт             |
| `DB_HOST`               | `localhost`         | Хост PostgreSQL       |
| `DB_NAME`               | `hvostid_matching`  | Имя базы              |
| `DB_USER`               | `hvostid`           | Пользователь БД       |
| `DB_PASSWORD`           | `hvostid`           | Пароль БД             |
| `LISTING_SERVICE_HOST`  | `localhost`         | Хост Listing Service  |
| `PASSPORT_SERVICE_HOST` | `localhost`         | Хост Passport Service |

## Локальный запуск

```bash
docker compose up -d postgres listing-service passport-service
./gradlew :matching-service:bootRun
```

## Зависимости

- **Обязательно:** PostgreSQL (база `hvostid_matching`).
- **Обязательно в рантайме:** Listing Service и Passport Service для
  рекомендаций и расчёта оценки.
