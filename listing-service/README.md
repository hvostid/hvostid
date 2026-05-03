**English** | [Русский](./README.ru.md)

# Listing Service

Owns pet listings: create, update, search, filter.

## Responsibilities

- CRUD for pet listings.
- Search and filter (city, species, price range, etc.).
- Enrich listings with passport data on demand by calling Passport
  Service.

## Endpoints

| Method | Path                         | Auth   | Notes                               |
|--------|------------------------------|--------|-------------------------------------|
| GET    | `/api/v1/listings`           | bearer | Search + filter via query params    |
| GET    | `/api/v1/listings/{id}`      | bearer |                                     |
| POST   | `/api/v1/listings`           | seller |                                     |
| PATCH  | `/api/v1/listings/{id}`      | seller | Owner only                          |
| DELETE | `/api/v1/listings/{id}`      | seller | Owner only                          |

Full spec at http://localhost:8082/swagger-ui.html.

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
