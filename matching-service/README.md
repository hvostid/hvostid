**English** | [Русский](./README.ru.md)

# Matching Service

Owns the buyer questionnaire and the owner-pet compatibility score.

## Responsibilities

- Buyer questionnaire CRUD with upsert semantics (one questionnaire
  per buyer; subsequent submissions overwrite).
- Compute a compatibility score between a buyer and a listing via
  **RestClient** calls to Listing Service and Passport Service (internal
  read). Response may include `degraded` and `degradedReason` when passport
  data is partial.
- Surface ranked recommendations to the buyer.

## Endpoints

| Method | Path                                   | Auth   | Notes                          |
|--------|----------------------------------------|--------|--------------------------------|
| GET    | `/api/v1/match/questionnaire`          | buyer  | Read current buyer's answers   |
| PUT    | `/api/v1/match/questionnaire`          | buyer  | Upsert                         |
| DELETE | `/api/v1/match/questionnaire`          | buyer  |                                |
| GET    | `/api/v1/match/recommendations`        | buyer  | Ranked listings                |
| POST   | `/api/v1/match/score`                  | buyer  | Score for a single listing     |

Full spec at http://localhost:8084/swagger-ui.html.

## Environment variables

| Name                    | Default             | Description           |
|-------------------------|---------------------|-----------------------|
| `SERVER_PORT`           | `8084`              | HTTP port             |
| `DB_HOST`               | `localhost`         | PostgreSQL host       |
| `DB_NAME`               | `hvostid_matching`  | Database name         |
| `DB_USER`               | `hvostid`           | Database user         |
| `DB_PASSWORD`           | `hvostid`           | Database password     |
| `LISTING_SERVICE_HOST`  | `localhost`         | Listing Service host  |
| `PASSPORT_SERVICE_HOST` | `localhost`         | Passport Service host |

## Run locally

```bash
docker compose up -d postgres listing-service passport-service
./gradlew :matching-service:bootRun
```

## Dependencies

- **Required:** PostgreSQL (`hvostid_matching` database).
- **Required at runtime:** Listing Service and Passport Service for
  recommendations and scoring.
