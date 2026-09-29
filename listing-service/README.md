**English** | [Русский](./README.ru.md)

# Listing Service

Owns pet listings: create, update, search, filter.

## Responsibilities

- CRUD for pet listings.
- Search and filter (city, species, price range, etc.).
- Enrich listings with passport data on demand by calling Passport
  Service.

## Endpoints

| Method | Path                         | Auth         | Notes                               |
|--------|------------------------------|--------------|-------------------------------------|
| GET    | `/api/v1/listings`           | bearer       | Search + filter via query params    |
| GET    | `/api/v1/listings/{id}`      | bearer       |                                     |
| POST   | `/api/v1/listings`           | seller       |                                     |
| GET    | `/api/v1/listings/draft`     | seller       | Restore own creation form; 204 if absent |
| PUT    | `/api/v1/listings/draft`     | seller       | Save own creation form without a passport |
| PATCH  | `/api/v1/listings/{id}`      | seller       | Owner only                          |
| DELETE | `/api/v1/listings/{id}`      | seller/admin      | Owner only                          |


Full spec at http://localhost:8082/swagger-ui.html.

Each seller has one saved creation form, stored separately from listings. It
survives navigation to passport creation and page reloads and is removed in the
same transaction as successful listing creation. Failed creation preserves it.

`GET /internal/listings/passports/{passportId}/has-active` is an internal-only
deletion guard for passport-service. It checks MODERATION and PUBLISHED listings,
including legacy `passport-` identifiers. The public `has-published` endpoint
continues to check PUBLISHED only. Internal endpoints are not routed by the gateway.

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
- **Optional at runtime:** Passport Service (only required when
  enriching listings with passport data; missing passport responses
  return the listing without enrichment).
- **Reverse dependencies:** Matching Service reads listings via HTTP.
