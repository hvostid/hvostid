**English** | [Русский](./README.ru.md)

# Listing Service

Owns pet listings: create, update, search, filter.

## Responsibilities

- CRUD for pet listings.
- Search and filter (city, species, price range, etc.).
- Validate passport ownership and reserve active references through Passport Service.

## Endpoints

| Method | Path                         | Auth         | Notes                               |
|--------|------------------------------|--------------|-------------------------------------|
| GET    | `/api/v1/listings`           | public       | Search + filter via query params    |
| GET    | `/api/v1/listings/{id}`      | optional     | Public only when PUBLISHED          |
| POST   | `/api/v1/listings`           | seller       |                                     |
| GET    | `/api/v1/listings/draft`     | seller       | Restore own creation form; 204 if absent |
| PUT    | `/api/v1/listings/draft`     | seller       | Save own creation form without a passport |
| PUT    | `/api/v1/listings/{id}`      | seller       | Owner only                          |
| DELETE | `/api/v1/listings/{id}`      | seller/admin      | Owner only                          |


Full spec at http://localhost:8082/swagger-ui.html.

Creation forms use `GET`, `PUT`, and `DELETE /api/v1/listings/drafts/{formId}` (UUID, seller-only).
PUT accepts incomplete fields plus `passportId` and a required `version` (0 for creation).
The response contains the next version; a stale save returns 409. A tab owns its form ID,
and deletes only that form after successful listing creation. Forms expire after 90 days.
The singular `/draft` endpoints remain for older clients.

Passport IDs are canonical positive 64-bit integers. Legacy `passport-` prefixes and leading
zeroes are normalized, including existing database records. Creation validates existence
and ownership. A listing's passport cannot subsequently be replaced. Editing a PUBLISHED
listing returns it to MODERATION and records the transition; edited data is not public until
approved again. Mutations and flag thresholds serialize on the listing row.

Before entering MODERATION/PUBLISHED, listing-service creates a durable retry job in a
separate transaction, then acquires a passport-side reservation. Passport deletion and
mutation lock the same passport row and reject active reservations. After commit (or a
rollback/crash), the retry worker locks the listing row and reconciles the reservation with
committed state. This prevents publication/delete races without a distributed transaction.
Release after archive/delete is asynchronous (normally within 10 seconds); unavailable
passport-service leaves durable jobs for retry. Old active listings are queued by migration,
and passport-service retains the legacy `has-active` check while holding its row lock.

`GET /internal/listings/passports/{passportId}/has-active` checks MODERATION/PUBLISHED.
The public `has-published` endpoint checks PUBLISHED only; optional `sellerId` additionally
requires that owner. Passport-service always supplies the actual passport owner, so legacy
wrong-owner listing references cannot expose private evidence. Invalid legacy identifiers
can still be archived/deleted, but cannot enter moderation/publication.
Internal endpoints are not routed
by the gateway and must remain on the private service network.

## Environment variables

| Name                    | Default            | Description           |
|-------------------------|--------------------|-----------------------|
| `SERVER_PORT`           | `8082`             | HTTP port             |
| `DB_HOST`               | `localhost`        | PostgreSQL host       |
| `DB_NAME`               | `hvostid_listing`  | Database name         |
| `DB_USER`               | `hvostid`          | Database user         |
| `DB_PASSWORD`           | `hvostid`          | Database password     |
| `PASSPORT_SERVICE_HOST` | `localhost`        | Passport Service host |

## Run locally

```bash
docker compose up -d postgres passport-service
./gradlew :listing-service:bootRun
```

## Dependencies

- **Required:** PostgreSQL (`hvostid_listing` database).
- **Required for creation/moderation:** Passport Service. Unavailability returns 503; unsafe writes do not proceed.
- **Reverse dependencies:** Matching Service reads listings via HTTP.

Draft deletion requires `DELETE /api/v1/listings/drafts/{formId}?version=N`; a newer saved version is preserved with HTTP 409.

Reservation revisions persist on passport-service. Acquire uses `2 * operationId`, reconciliation uses `2 * operationId + 1`; older or duplicate revisions cannot resurrect a released reference after an HTTP timeout. Do not reset the outbox sequence independently of passport data during restore.

On upgrade, the first missing per-form GET atomically claims the seller's legacy `/draft` as version1. Other tabs cannot consume it twice; existing forms are never overwritten.
