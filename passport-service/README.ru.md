[English](./README.md) | **Русский**

# Passport Service

Владеет цифровым паспортом питомца, сопроводительными документами и
оценкой доверия.

> **Статус.** Сервис сейчас стартует с настроенной БД и MinIO-бакетом,
> но не выставляет ни одного бизнес-эндпоинта. CRUD паспортов и загрузка
> документов отслеживаются в T19 / T20. Этот README расширится, когда
> они появятся.

## Зоны ответственности

- Хранение и выдача цифровых паспортов питомцев.
- Приём загрузки документов (прививочные карты, ветеринарные
  сертификаты) и сохранение их в MinIO.
- Расчёт оценки доверия по полноте паспорта и валидации документов.

## Эндпоинты

Спецификация будет доступна по http://localhost:8083/swagger-ui.html
после появления контроллеров.

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

Имя MinIO-бакета (`pet-documents`) и лимиты multipart upload
(10 MB / 20 MB) зафиксированы в
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
