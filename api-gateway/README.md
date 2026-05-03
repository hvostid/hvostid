# API Gateway

Spring Cloud Gateway (server-MVC) front door for the platform.

## Responsibilities

- Route `/api/v1/**` traffic to the right service.
- Validate incoming Bearer tokens via Auth Service introspection and
  inject `X-User-Id` / `X-User-Roles` for downstream services
  (`TokenIntrospectionFilter`).
- Generate / propagate `X-Request-Id` and bind it to MDC for log
  correlation (`RequestIdFilter`).
- Token-bucket rate limit per client IP (`RateLimitFilter`).

The full request lifecycle is documented in
[`docs/architecture.md`](../docs/architecture.md#request-lifecycle).

## Routing

| Path prefix          | Target service              |
|----------------------|-----------------------------|
| `/api/v1/auth/**`    | Auth Service (`:8081`)      |
| `/api/v1/profile/**` | Auth Service (`:8081`)      |
| `/api/v1/listings/**`| Listing Service (`:8082`)   |
| `/api/v1/passports/**`| Passport Service (`:8083`) |
| `/api/v1/match/**`   | Matching Service (`:8084`)  |

Public paths (no token required): `/api/v1/auth/login`,
`/api/v1/auth/register`, `/actuator/**`.

## Environment variables

| Name                      | Default     | Description                       |
|---------------------------|-------------|-----------------------------------|
| `SERVER_PORT`             | `8080`      | HTTP port                         |
| `AUTH_SERVICE_HOST`       | `localhost` | Auth Service hostname             |
| `LISTING_SERVICE_HOST`    | `localhost` | Listing Service hostname          |
| `PASSPORT_SERVICE_HOST`   | `localhost` | Passport Service hostname         |
| `MATCHING_SERVICE_HOST`   | `localhost` | Matching Service hostname         |

Rate limit (`hvostid.rate-limit.replenish-rate` / `burst-capacity`) and
auth (`hvostid.auth.introspect-url` / `introspect-timeout`) are
configured in
[`application.yml`](./src/main/resources/application.yml).

## Run locally

Bring up dependencies, then start the gateway from your IDE or:

```bash
docker compose up -d postgres minio minio-init auth-service listing-service passport-service matching-service
./gradlew :api-gateway:bootRun
```

## Dependencies

- **Required at runtime:** Auth Service (introspection).
- **Required to forward traffic:** whichever downstream service the
  current path targets.
