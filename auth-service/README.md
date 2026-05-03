# Auth Service

Owner of users, sessions, opaque access/refresh tokens, profile data,
and roles.

## Responsibilities

- Registration, login, logout, refresh.
- Token introspection on the **internal** path
  `POST /internal/auth/introspect` (called by the Gateway, not exposed
  externally).
- Profile read/update.
- Role management.

Tokens are opaque random strings stored in the `sessions` table; there
is no JWT or signing key. See
[`docs/architecture.md`](../docs/architecture.md#authentication-and-authorization)
for the rationale.

## Endpoints

| Method | Path                              | Auth      | Notes                                |
|--------|-----------------------------------|-----------|--------------------------------------|
| POST   | `/api/v1/auth/register`           | public    |                                      |
| POST   | `/api/v1/auth/login`              | public    | Returns access + refresh tokens      |
| POST   | `/api/v1/auth/refresh`            | public    | Rotates refresh token                |
| POST   | `/api/v1/auth/logout`             | bearer    |                                      |
| GET    | `/api/v1/profile/me`              | bearer    |                                      |
| PATCH  | `/api/v1/profile/me`              | bearer    |                                      |
| POST   | `/api/v1/profile/me/roles`        | admin     |                                      |
| POST   | `/internal/auth/introspect`       | internal  | Not routed through the Gateway       |

Full spec at http://localhost:8081/swagger-ui.html.

## Environment variables

| Name              | Default          | Description                     |
|-------------------|------------------|---------------------------------|
| `SERVER_PORT`     | `8081`           | HTTP port                       |
| `DB_HOST`         | `localhost`      | PostgreSQL host                 |
| `DB_NAME`         | `hvostid_auth`   | Database name                   |
| `DB_USER`         | `hvostid`        | Database user                   |
| `DB_PASSWORD`     | `hvostid`        | Database password               |

Token TTLs (`hvostid.auth.access-token-ttl`,
`hvostid.auth.refresh-token-ttl`) are configured in
[`application.yml`](./src/main/resources/application.yml). A
production override lives in
[`application-prod.yml`](./src/main/resources/application-prod.yml).

## Run locally

```bash
docker compose up -d postgres
./gradlew :auth-service:bootRun
```

## Dependencies

- **Required:** PostgreSQL (`hvostid_auth` database).
- **Other services do not call this service externally** -- only the
  Gateway calls the internal introspection endpoint.
