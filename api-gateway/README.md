**English** | [Русский](./README.ru.md)

# API Gateway

Spring Cloud Gateway MVC routes external API requests to internal services.

## Request processing

1. RequestIdFilter establishes the request ID and logging context.
2. IdentityHeaderFilter removes every incoming X-User-Id and X-User-Roles header,
   including on public routes.
3. RateLimitFilter checks the immediate client address or a configured trusted proxy chain.
4. TokenIntrospectionFilter validates Bearer credentials and sets authenticated
   identity headers. Missing/inactive credentials produce 401; unavailable
   introspection produces 503, preserving the distinction for clients.

Public paths are explicitly method scoped in application.yml. Catalog search is
public; only numeric listing detail paths permit optional authentication. My
listings and saved drafts always require authentication. Internal service routes
are never gateway-routed.

## Routes

| Prefix | Target |
| --- | --- |
| /api/v1/auth/**, /api/v1/profile/**, /api/v1/users/** | Auth (:8081) |
| /api/v1/listings/**, /api/v1/moderation/** | Listing (:8082) |
| /api/v1/passports/** | Passport (:8083) |
| /api/v1/match/** | Matching (:8084) |

## Configuration

Service hosts use AUTH_SERVICE_HOST, LISTING_SERVICE_HOST,
PASSPORT_SERVICE_HOST and MATCHING_SERVICE_HOST, each defaulting to localhost.
SERVER_PORT defaults to 8080. Introspection has a 3-second timeout.

The token-bucket limits default to 60 requests/second and a burst of 120 per
client. Authentication endpoints have an independent 1/second, burst-10 quota.
The cache holds at most 10,000 client/category buckets, expiring idle entries
after 15 minutes; active entries are not evicted to reset quotas. At capacity,
new clients receive 429 until capacity is available. Responses include Retry-After.

X-Forwarded-For is ignored by default. HVOSTID_RATE_LIMIT_TRUSTED_PROXIES is an
explicit comma-separated list of immediate proxy IPs, never an arbitrary client
list. Configure the edge to overwrite X-Forwarded-For with the remote address;
the gateway walks a trusted chain from right to left to the nearest untrusted
peer. Do not expose the gateway directly when trusting a proxy. Limits are
per gateway instance: keep a single gateway replica, or use a shared limiter
before horizontal scaling.

Production should expose only the TLS edge. Actuator is for private monitoring,
not public nginx routing. Full property defaults are in src/main/resources/application.yml.

## Local run

```bash
docker compose up -d postgres minio minio-init auth-service listing-service passport-service matching-service
./gradlew :api-gateway:bootRun
./gradlew :api-gateway:test
```
