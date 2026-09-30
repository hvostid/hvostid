**English** | [Русский](./architecture.ru.md)

# Architecture

Five Spring Boot services sit behind a Spring Cloud Gateway MVC gateway and a
React SPA served by Nginx. Each domain owns a separate PostgreSQL database and
application role. Passport documents live in MinIO; Redis stores short-lived
media tickets. See the [operations runbook](./operations.md) for deployment,
backups, restore drills, metrics, alerting and TLS.

| Service | Responsibilities | Remote dependencies |
|---|---|---|
| API Gateway | Identity header sanitization, token introspection, routing and bounded rate limiting | Auth |
| Auth | Accounts, hashed opaque tokens, profiles, roles, sessions and account recovery | Configured SMTP |
| Listing | Catalog, seller drafts, moderation, flags and passport references | Passport |
| Passport | Animal records, vaccinations, documents, trust score and reference/deletion guards | Listing, MinIO, Redis |
| Matching | Buyer questionnaires, compatibility explanations and recommendations | Listing, Passport |

The `common` module contains shared security headers, role names, ProblemDetails,
error handling and test fixtures. It does not share domain entities or database tables.

## Request lifecycle

1. `RequestIdFilter` establishes request correlation.
2. `IdentityHeaderFilter` removes incoming identity headers from every request,
   including public and optional-auth routes.
3. `RateLimitFilter` applies a bounded per-client token bucket before authentication
   work (60 tokens/second, burst 120; auth endpoints 1/second, burst 10). Client
   entries expire after 15 minutes and are capped at 10,000. Limits are per gateway
   process; use a shared edge limiter before horizontally scaling the gateway.
4. `TokenIntrospectionFilter` follows configured public/optional-auth policies,
   validates bearer tokens with Auth and writes trusted `X-User-Id` and
   `X-User-Roles` only after successful introspection. Auth unavailability returns
   a retryable availability error, not an invalid-token response.
5. Domain services enforce permissions and ownership with Spring Security and
   service-level checks. Internal endpoints are not exposed by gateway routes.

Nginx overwrites X-Forwarded-For with the verified client address. The gateway
trusts forwarded addresses only from the configured frontend IP. The optional TLS
edge has its own explicitly trusted address in Nginx. Never expose domain service
ports to the Internet: the service identity headers rely on this private network
boundary, not on cryptographic service identity.

## Authentication

Auth returns opaque access tokens (30-minute default) and rotating refresh tokens
(7-day default). Only SHA-256 token digests are stored in PostgreSQL. Refresh,
password reset and revoke-all synchronize against the account to avoid concurrent
rotation races. Cleanup uses refresh expiry, so an expired access token does not
prematurely delete a refreshable session. Public authentication and optional catalog
routes are defined in `api-gateway/src/main/resources/application.yml`.

## Persistence and cross-service consistency

`docker/init-databases.sh` creates four databases and distinct least-privilege roles:
`hvostid_auth`, `hvostid_listing`, `hvostid_passport`, `hvostid_matching`. Application
services do not use the PostgreSQL administrator. Upgrades of existing volumes use
`scripts/migrate-db-roles.sh` with application writers stopped. Flyway migrations
remain owned by each service and Hibernate validates schema compatibility.

| Database | Main tables |
|---|---|
| Auth | users, user_roles, sessions, account recovery tokens |
| Listing | listings, listing_drafts, listing_flags, listing_status_history |
| Passport | pet_passports, vaccinations, passport_documents, passport references and durable cleanup jobs |
| Matching | buyer_questionnaire |

Listing validates canonical passport ownership and maintains a durable reference
with Passport. Passport locks mutations against active references, protecting
moderated/published animals from edits and deletion. Failed remote synchronization
and object cleanup are retried durably. These are inter-service HTTP contracts;
there are no cross-database joins or distributed SQL transactions.

RestClient instances use the auto-configured builder for trace propagation and
explicit connect/read timeouts. Matching uses Resilience4j circuit breakers:
passport failures are recorded before fallback; a missing passport can yield a
marked degraded score. Errors use ProblemDetails. Recommendations filter species
and breed, coalesce only active calculations for an immutable questionnaire revision,
and read current remote data on subsequent requests. Shared workers, admission,
10,000 scanned-listing and 30-second budgets prevent unbounded fan-out. Exceeded
budgets return 503 without a silently partial ranking.

## Deployment and operations

Development exposes service ports for local tools. Production exposes the frontend
and binds the gateway diagnostic port to loopback; domain services and storage stay
private. `docker-compose.tls.yml` provides an optional Caddy TLS edge.
`docker-compose.observability.yml` enables private Prometheus scraping, Grafana,
Alertmanager and OpenTelemetry traces in Tempo. Dashboards and alert rules are
versioned; notification receivers require operator configuration. OTLP export is
disabled outside the observability profile.

MinIO server and client are built from pinned upstream commits in `docker/minio/`.
The matching source-build test image remains under `docker/minio-test/`.
Critical base images use immutable digests and Gradle dependencies use lockfiles.
PR CI runs backend checks, frontend lint/tests/build/audit, browser regressions,
a fresh Compose business smoke (including uploads through Nginx), and an isolated
PostgreSQL/object restore drill. CD publishes a SHA candidate, scans and smoke-tests
it, then promotes `latest`; a failed validation never promotes the candidate.
