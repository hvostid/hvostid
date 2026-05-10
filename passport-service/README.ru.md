[English](./README.md) | **Русский**

# Passport Service

Владеет цифровым паспортом питомца, сопроводительными документами и
оценкой доверия.

> **Статус.** Сервис сейчас поддерживает базовый CRUD цифровых паспортов
> питомцев. Загрузка документов и фото отслеживается отдельно.

## Зоны ответственности

- Хранение и выдача цифровых паспортов питомцев.
- Приём загрузки документов (прививочные карты, ветеринарные
  сертификаты) и сохранение их в MinIO.
- Расчёт оценки доверия по полноте паспорта и валидации документов.

## Эндпоинты

Спецификация доступна по http://localhost:8083/swagger-ui.html.

- `POST /api/v1/passports` -- создать паспорт питомца. `sellerId`
  берётся из `X-User-Id`, пользователь должен иметь роль `SELLER`.
- `GET /api/v1/passports/{petId}` -- получить паспорт с прививками,
  доступно authenticated user.
- `PUT /api/v1/passports/{petId}` -- частично обновить паспорт,
  доступно только владельцу с ролью `SELLER`.

## Переменные окружения

| Имя                | Default             | Описание                          |
|--------------------|---------------------|-----------------------------------|
| `SERVER_PORT`      | `8083`              | HTTP-порт                         |
| `DB_HOST`          | `localhost`         | Хост PostgreSQL                   |
| `DB_NAME`          | `hvostid_passport`  | Имя базы                          |
| `DB_USER`          | `hvostid`           | Пользователь БД                   |
| `DB_PASSWORD`      | `hvostid`           | Пароль БД                         |
| `MINIO_HOST`       | `localhost`         | Хост MinIO                        |
| `MINIO_ACCESS_KEY` | `minioadmin`        | Access key MinIO                  |
| `MINIO_SECRET_KEY` | `minioadmin`        | Secret key MinIO                  |
| `MINIO_DOCUMENTS_BUCKET` | `pet-documents` | Бакет для документов паспорта |
| `MINIO_PHOTOS_BUCKET` | `pet-photos` | Бакет для фото паспорта |

Лимиты multipart upload (10 MB / 20 MB) зафиксированы в
[`application.yml`](./src/main/resources/application.yml).

## Локальный запуск

```bash
docker compose up -d postgres minio minio-init
./gradlew :passport-service:bootRun
```

## Зависимости

- **Обязательно:** PostgreSQL (база `hvostid_passport`), MinIO.
- **Обратные зависимости:** Listing Service и Matching Service оба
  читают данные паспорта через HTTP.
