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
| POST   | `/api/v1/match/questionnaire`          | buyer  | Upsert                         |
| GET    | `/api/v1/match/recommendations`        | buyer  | Ranked listings                |
| POST   | `/api/v1/match/score`                  | buyer  | Score for a single listing     |

`POST /api/v1/match/score` returns score breakdown plus explanation fields:

```json
{
  "score": 78,
  "level": "GOOD",
  "factors": [
    { "name": "living_space", "score": 18, "maxScore": 20, "comment": "..." }
  ],
  "summary": "Good match overall. ...",
  "tips": ["Consider enrolling in a pet training course before adoption"],
  "adaptationPlan": [
    {
      "dayRange": "1-3",
      "title": "Getting to know each other",
      "tasks": ["Set up a quiet corner", "..."]
    }
  ],
  "degraded": false,
  "degradedReason": null
}
```

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

## Freshness and recommendation limits

Completed scores and recommendation lists are not cached: each request reads the
current questionnaire, published catalog and passports. Concurrent recommendation
requests for the same questionnaire revision share only the active computation;
a changed questionnaire immediately starts a separate computation. Degraded passport
results are retried on the next request (subject to the circuit breaker).

All catalog pages are considered, including candidates beyond the first 200.
Species and exact case-insensitive breed preferences filter candidates before
passport lookups. English and Russian species names are supported. Ranking is
stable by score descending, then listing ID. The total count describes matching
candidates, not all catalog entries. Like ordinary offset catalog pagination,
concurrent catalog edits can change the next page; the next request starts fresh.

One shared worker pool limits outbound recommendation calls. `MATCHING_PARALLELISM`
(default `16`) controls active workers and `MATCHING_MAX_COMPUTATIONS` (default `8`)
limits different concurrent computations. Queue overload returns retryable HTTP 503
instead of spawning unbounded work. Pagination uses long offsets and an out-of-range
page returns an empty content array. These variables may be overridden for capacity
testing; increase them only after measuring upstream capacity.

`readyForAdaptation=false` returns preparation tasks before adoption, not the 14-day
arrival plan. Temperament accepts structured `FRIENDLY`, `NERVOUS`, `CHALLENGING`,
`ACTIVE` codes and legacy English/Russian descriptions through a shared vocabulary.
Unrecognized descriptions remain unknown; the score is guidance, not verification
of an individual animal's behavior.

Regression coverage: `MatchingFreshnessTest` exercises a 251-candidate cold catalog,
bounded parallelism, request coalescing, changed questionnaires, removed listings,
recovery from missing passports and maximum integer page values.
`PassportCircuitBreakerTest` verifies real HTTP failures open the registered circuit,
404 does not count as an availability failure, and successful half-open probes recover.
Recommendation computations have an explicit budget: at most
`MATCHING_MAX_SCANNED_LISTINGS=10000` catalog rows and
`MATCHING_COMPUTATION_TIMEOUT=30s`. Exceeding either returns HTTP 503 with no
partial ranking; retry later or use catalog filters. Regression tests exercise
both limits. These limits bound memory and work even while listings are added.
