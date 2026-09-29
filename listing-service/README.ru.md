[English](./README.md) | **Русский**

# Listing Service

Владеет объявлениями о питомцах: создание, обновление, поиск,
фильтрация.

## Зоны ответственности

- CRUD объявлений о питомцах.
- Поиск и фильтрация (город, вид, диапазон цен и т.д.).
- Обогащение объявлений данными паспорта по запросу через Passport
  Service.

## Эндпоинты

| Метод  | Путь                         | Auth         | Заметки                             |
|--------|------------------------------|--------------|-------------------------------------|
| GET    | `/api/v1/listings`           | bearer       | Поиск + фильтры через query-параметры |
| GET    | `/api/v1/listings/{id}`      | bearer       |                                     |
| POST   | `/api/v1/listings`           | seller       |                                     |
| GET    | `/api/v1/listings/draft`     | seller       | Восстановить свою форму; 204, если её нет |
| PUT    | `/api/v1/listings/draft`     | seller       | Сохранить свою форму без паспорта |
| PATCH  | `/api/v1/listings/{id}`      | seller       | Только владелец                     |
| DELETE | `/api/v1/listings/{id}`      | seller/admin      | Только владелец                     |


Полная спецификация: http://localhost:8082/swagger-ui.html.

Для каждого продавца хранится одна незавершённая форма отдельно от объявлений.
Она сохраняется при переходе к созданию паспорта и перезагрузке страницы.
Успешное создание объявления удаляет форму в той же транзакции; ошибка сохраняет её.

Внутренний `GET /internal/listings/passports/{passportId}/has-active` проверяет
связи с объявлениями в MODERATION и PUBLISHED перед удалением паспорта, включая
старые ID с префиксом `passport-`. Публичный `has-published` по-прежнему проверяет
только PUBLISHED. Gateway не маршрутизирует внутренние эндпоинты.

## Переменные окружения

| Имя                     | Default            | Описание              |
|-------------------------|--------------------|-----------------------|
| `SERVER_PORT`           | `8082`             | HTTP-порт             |
| `DB_HOST`               | `localhost`        | Хост PostgreSQL       |
| `DB_NAME`               | `hvostid_listing`  | Имя базы              |
| `DB_USER`               | `hvostid`          | Пользователь БД       |
| `DB_PASSWORD`           | `hvostid`          | Пароль БД             |
| `PASSPORT_SERVICE_HOST` | `localhost`        | Хост Passport Service |

## Локальный запуск

```bash
docker compose up -d postgres passport-service
./gradlew :listing-service:bootRun
```

## Зависимости

- **Обязательно:** PostgreSQL (база `hvostid_listing`).
- **Опционально в рантайме:** Passport Service (нужен только при
  обогащении объявлений данными паспорта; при отсутствии паспорта
  объявление возвращается без обогащения).
- **Обратные зависимости:** Matching Service читает объявления через
  HTTP.
