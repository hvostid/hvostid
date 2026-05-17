**English** | [Русский](./README.ru.md)

# Passport Service

Owns the digital pet passport, supporting documents, and trust score.

> **Status.** The service supports basic CRUD for digital pet passports.
> Document and photo uploads are tracked separately.

## Responsibilities

- Store and serve digital pet passports.
- Accept document uploads (vaccination records, vet certificates) and
  persist them to MinIO.
- Compute a trust score from passport completeness and document
  validation.

## Endpoints

Spec is served at http://localhost:8083/swagger-ui.html.

- `POST /api/v1/passports` -- create a pet passport. `sellerId` is read
  from `X-User-Id`; the user must have the `SELLER` role.
- `GET /api/v1/passports/{petId}` -- get a passport with vaccinations,
  available to the owner, `MODERATOR`, and `ADMIN`.
- `PUT /api/v1/passports/{petId}` -- partially update a passport,
  available only to its owner with the `SELLER` role.
- `POST /api/v1/passports/{petId}/docs` -- upload a photo or document,
  available only to the owner with the `SELLER` role.
- `GET /api/v1/passports/{petId}/docs` -- list document metadata,
  available to the owner, `MODERATOR`, and `ADMIN`.
- `GET /api/v1/passports/{petId}/docs/{docId}` -- get a temporary
  download URL, returned as `302 Found`.
- `DELETE /api/v1/passports/{petId}/docs/{docId}` -- delete a document,
  available only to the owner with the `SELLER` role.

Documents are accepted as `jpg`, `jpeg`, `png`, or `pdf`. Maximum file
size is 10 MB.

## Environment variables

| Name               | Default             | Description       |
|--------------------|---------------------|-------------------|
| `SERVER_PORT`      | `8083`              | HTTP port         |
| `DB_HOST`          | `localhost`         | PostgreSQL host   |
| `DB_NAME`          | `hvostid_passport`  | Database name     |
| `DB_USER`          | `hvostid`           | Database user     |
| `DB_PASSWORD`      | `hvostid`           | Database password |
| `MINIO_HOST`       | `localhost`         | MinIO host        |
| `MINIO_ACCESS_KEY` | `minioadmin`        | MinIO access key  |
| `MINIO_SECRET_KEY` | `minioadmin`        | MinIO secret key  |
| `MINIO_DOCUMENTS_BUCKET` | `pet-documents` | Documents bucket  |
| `MINIO_PHOTOS_BUCKET` | `pet-photos` | Photo bucket      |

Multipart upload limits (10 MB / 20 MB) are pinned in
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
