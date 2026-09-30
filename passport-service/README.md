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
- `GET /api/v1/passports/my` -- get the authenticated user's passports
  as a paginated response. Supports `page`, `size`, and `sort`.
- `GET /api/v1/passports/{petId}` -- get a passport with vaccinations,
  available to the owner, `MODERATOR`, and `ADMIN`.
- `PUT /api/v1/passports/{petId}` -- partially update a passport,
  available only to its owner with the `SELLER` role.
- `DELETE /api/v1/passports/{petId}` -- delete a passport owned by the
  seller. Deletion is blocked while the passport is referenced by a
  published listing or a listing under moderation. An unavailable or invalid
  listing-service response returns 503 and leaves the passport intact.
- `POST /api/v1/passports/{petId}/docs` -- upload a photo or document,
  available only to the owner with the `SELLER` role.
- `GET /api/v1/passports/{petId}/docs` -- list document metadata,
  available to the owner, `MODERATOR`, and `ADMIN`. Other authenticated
  users see only `PHOTO` entries when the passport is referenced by a
  published listing.
- `GET /api/v1/passports/{petId}/docs/{docId}` -- get a temporary
  single use media URL for the document content.
- `GET /api/v1/passports/{petId}/cover` -- stream the public catalog
  cover photo for a passport referenced by a published listing.
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

Integration tests build MinIO from pinned upstream source through
`:passport-service:prepareMinioTestImage`; `./gradlew check` and `./gradlew build`
include this automatically. Docker must be running. See
[`docker/minio-test`](../docker/minio-test/README.md) for the source revision and
cache behavior.

- **Required:** PostgreSQL (`hvostid_passport` database), MinIO, Redis.
- **Outbound dependencies:** Listing Service is queried before exposing
  public photos/trust score and before deleting a passport.
- **Reverse dependencies:** Listing Service and Matching Service both read
  passport data via HTTP.

## Integrity, scoring, and file lifecycle

Create/update accepts `vaccinations: [{name,date,nextDate}]`. Omission preserves entries;
`[]` clears them. Entries are limited to 100, names to 255 characters, dates cannot be in
the future or before birth, and nextDate cannot precede date. Client-supplied verification
is never trusted. `/my` includes `firstPhotoDocumentId`; covers are batched and vaccinations
use batch fetching, so a page does not issue one database query per passport.

The score is evidence completeness, not a medical guarantee or seller rating. Available
weights are profile 25, photo 20, vaccination certificate 20, vet record 15, dated
vaccinations 15, moderation 5. `maximum` is 100 and `availableComponents` lists these six
inputs. Legacy sellerRating/sellerSales fields remain zero and are excluded from the scale.
A listing approval marks the reserved passport moderated; edits reset that marker.

Active references (`MODERATION`/`PUBLISHED`) prevent passport/data/file edits and deletion.
Archive the listing first; asynchronous reservation release normally takes up to 10 seconds.
Internal PUT/DELETE `/internal/passports/{id}/references/{listingId}` is idempotent and
serialized with deletion. These endpoints must remain on the private service network.
Buyer/public media and evidence-score checks require a PUBLISHED listing belonging to the
actual passport owner; pre-upgrade references created by another seller grant no access.

Uploads are limited to 20 files / 100 MB per passport and 10 MB per file. PHOTO accepts
only decoded JPEG/PNG (up to 25 megapixels); supplied extensions and MIME must match bytes.
PDFs are parsed using PDFBox: 1-100 pages, no encryption, actions, or attachments. This is
format validation, not certification of document claims. PDF originals are preserved.

Object removal is recorded in `object_cleanup_jobs` in the metadata deletion transaction.
A worker retries storage failures every minute. Uploads register durable orphan cleanup
before contacting S3, and cancel it atomically with committed metadata. Cleanup locks the
passport before the job, so an in-progress upload is not mistaken for an orphan. Delayed
upload cleanup starts after one hour; referenced objects are never removed. Operators can
inspect `attempts` and `available_at` to diagnose a backlog; retrying is idempotent.
