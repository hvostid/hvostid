**English** | [Русский](./README.ru.md)

# Passport Service

Owns the digital pet passport, supporting documents, and trust score.

> **Status.** The service currently boots with its database and MinIO
> bucket configured but does not yet expose business endpoints. CRUD
> for passports and document upload are tracked in T19 / T20. This
> README will grow once those land.

## Responsibilities

- Store and serve digital pet passports.
- Accept document uploads (vaccination records, vet certificates) and
  persist them to MinIO.
- Compute a trust score from passport completeness and document
  validation.

## Endpoints

Spec will be served at http://localhost:8083/swagger-ui.html once
controllers land.

## Environment variables

| Name               | Default             | Description                       |
|--------------------|---------------------|-----------------------------------|
| `SERVER_PORT`      | `8083`              | HTTP port                         |
| `DB_HOST`          | `localhost`         | PostgreSQL host                   |
| `DB_NAME`          | `hvostid_passport`  | Database name                     |
| `DB_USER`          | `hvostid`           | Database user                     |
| `DB_PASSWORD`      | `hvostid`           | Database password                 |
| `MINIO_HOST`       | `localhost`         | MinIO host                        |
| `MINIO_ACCESS_KEY` | `minioadmin`        | MinIO access key                  |
| `MINIO_SECRET_KEY` | `minioadmin`        | MinIO secret key                  |

The MinIO bucket name (`pet-documents`) and multipart upload limits
(10 MB / 20 MB) are pinned in
[`application.yml`](./src/main/resources/application.yml).

## Run locally

```bash
docker compose up -d postgres minio minio-init
./gradlew :passport-service:bootRun
```

## Dependencies

- **Required:** PostgreSQL (`hvostid_passport` database), MinIO.
- **Reverse dependencies:** Listing Service and Matching Service both
  read passport data via HTTP.
