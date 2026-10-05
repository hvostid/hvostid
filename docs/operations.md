# Operations runbook

## Deployment boundary and secrets

Use `docker-compose.prod.yml` with a private `.env.prod` copied from
`.env.prod.example`. Set unique PostgreSQL administrator and four application-role
passwords, MinIO credentials, public origins and SMTP credentials. Do not commit
secrets or bake them into images; `.dockerignore` excludes all local environment
files and backups. The application roles cannot connect to other domain databases.

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml pull --ignore-buildable
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build --wait --wait-timeout 300
```

MinIO and mc are built locally from immutable upstream commits, not unavailable
Docker Hub community tags. Source revisions and AGPL license files are preserved
in `docker/minio/Dockerfile`. Keep build network access available on first deployment,
or publish that exact source build in your controlled registry before deployment.
Both upstream community repositories are archived. This build applies pinned
dependency updates and local guards for a standalone S3 deployment. The browser
console, STS, replication, unsigned-trailer uploads and S3 Select are disabled;
administration uses the bundled `mc` and static credentials. Read
[`docker/minio/README.md`](../docker/minio/README.md) before enabling additional
features or changing the deployment topology. CI separately checks upstream
advisories and the compiled Go binaries on every security run.

Domain services, PostgreSQL, Redis and object storage must stay private. Internal
user headers are trusted at that boundary. Production gateway port 8080 is bound
only to loopback. Never route `/internal/**` or `/actuator/**` through the public edge.
Use one gateway replica unless an upstream shared rate limiter is configured.

For TLS, set `PUBLIC_HOST` to a hostname whose DNS points at the server, then add
`-f docker-compose.tls.yml` to the production command. Caddy obtains and renews the
certificate and retains it in its own volume. Port 80/443 must be reachable for
certificate issuance. Nginx trusts the edge at 172.30.44.11; gateway trusts frontend
at 172.30.44.10. Dynamic containers use `HVOSTID_DYNAMIC_RANGE=172.30.44.128/25`, reserving
those trusted static addresses. If changing subnet/IPs, change the DHCP range
and explicit Nginx trust rule too.
SMTP is disabled by default; enable it after configuring a working provider and a
correct `AUTH_PUBLIC_BASE_URL`. Set `AUTH_MAIL_ENCRYPTION_KEY` to a randomly generated
32-byte base64 key; the durable mail outbox stores encrypted payloads. Back up the
key separately in your secret manager and restore it with the database. Do not
rotate it while pending encrypted messages remain. Verify delivery and password
reset before public use.

### Existing databases

Existing volumes are preserved. They will not rerun initialization automatically.
Back them up before applying new migrations. During a maintenance window, stop
application services, recreate only the PostgreSQL container with the new environment
and initialization script mount, and transfer ownership:

```bash
export COMPOSE_FILE=docker-compose.prod.yml
# Export the same production variables, or use COMPOSE_ENV_FILES=.env.prod.
export COMPOSE_ENV_FILES=.env.prod
docker compose stop frontend api-gateway auth-service listing-service passport-service matching-service
docker compose up -d --wait postgres
bash scripts/migrate-db-roles.sh
docker compose up -d --wait --wait-timeout 300
```

The script refuses running application writers and transfers existing tables and
sequences to the corresponding application role. Do not remove data volumes to upgrade.

## Consistent backups

`scripts/backup.sh NEW_DIRECTORY` stops currently running application writers,
dumps all four databases in PostgreSQL custom format, mirrors both object buckets,
writes image identities and checksums, then restarts the previously running services
using an exit trap. The destination must not exist. File permissions use umask 077.
Backup files contain private data: encrypt before off-host copying and restrict access.

```bash
COMPOSE_FILE=docker-compose.prod.yml COMPOSE_ENV_FILES=.env.prod \
  bash scripts/backup.sh /srv/hvostid-backups/2026-09-30T030000Z
```

Initial operational objectives: RPO 24 hours and RTO 30 minutes. Arrange daily
backups and retain at least 7 daily and 4 weekly off-host copies. These are operator
objectives, not measured guarantees for an unknown production dataset. Monitor the
backup exit code, age of the latest completed-at.txt and off-host copy completion.
Measure a representative restore before committing to an availability target.

## Restore and rollback

Create an empty target environment with the same pinned image versions and fresh
storage volumes. Initialize PostgreSQL, MinIO and buckets, leaving application writers
stopped. The restore script checks SHA-256 checksums and refuses nonempty databases,
nonempty buckets or running application services. It never deletes existing data.

```bash
COMPOSE_FILE=docker-compose.restore.yml COMPOSE_PROJECT_NAME=hvostid-restore \
  docker compose up -d --wait postgres minio
COMPOSE_FILE=docker-compose.restore.yml COMPOSE_PROJECT_NAME=hvostid-restore \
  docker compose run --rm minio-init
COMPOSE_FILE=docker-compose.restore.yml COMPOSE_PROJECT_NAME=hvostid-restore \
  bash scripts/restore.sh /srv/hvostid-backups/2026-09-30T030000Z --empty-target
```

Run `RESTORE_DRILL_DISPOSABLE=true bash scripts/test-restore.sh` only against a
disposable development/CI source. It inserts unique test markers, backs up, restores
into a separate project with its own volumes and no host ports, verifies both a SQL
row and object bytes, then removes only that temporary target. PR CI executes it.
The source test markers are removed afterward. Backups remain available for inspection.

CD publishes SHA tags first. `latest` is promoted only after image scans and real
application smoke checks pass. Deploy a known SHA, record the previous SHA, and revert
`IMAGE_TAG` to that SHA for an application rollback. Flyway schema changes may prevent
an older application from starting: then restore the matching backup into an empty
environment and switch traffic after validation, preserving the failed environment.

## Metrics, alerts and traces

Set `GRAFANA_ADMIN_PASSWORD`, then add `-f docker-compose.observability.yml` to your
normal Compose command. Grafana is bound to `127.0.0.1:3001`, Prometheus to 9091 and
Alertmanager to 9093; access remotely through an authenticated tunnel. The dashboard
shows availability, request rate, HTTP 5xx, p95 latency and database pool pressure.
Alert rules cover missing services, 5xx ratio, slow requests and pending DB connections.
The shipped Alertmanager receiver shows local alerts without sending external messages;
configure your delivery integration and test it before relying on notifications.

Tracing uses the Spring OpenTelemetry starter and auto-configured RestClient builders.
The observability override exports sampled spans to internal Tempo. Default sampling
is 10%; use `TRACE_SAMPLE_PROBABILITY=1.0` only for a short diagnostic session.
OTLP export is disabled in normal local/dev runs and tests. Do not place personal data
or tokens in baggage, span tags or log messages.

Validate rules using `promtool test rules docker/observability/alerts.test.yml`.
In a disposable stack, stop passport-service for more than one minute and verify
`HvostidServiceDown` fires in Prometheus and reaches the configured receiver; restart
it and verify resolution. Exercise a request through Nginx and inspect its trace in
Grafana's Tempo datasource. Keep diagnostics endpoints on the private network.

## Dependency and build updates

Runtime image digests, source commits, the Gradle distribution checksum and six
module lockfiles are checked in. Update intentionally with Dependabot and review
release compatibility. Spring Cloud 2025.1 uses the Boot 4.0 line; do not combine
an unsupported Boot upgrade with a disabled compatibility verifier.

```bash
./gradlew resolveAndLockDependencies --write-locks
./gradlew build
node scripts/audit-jvm-dependencies.mjs
npm ci --prefix frontend
npm audit --prefix frontend --audit-level=high
```

The OSV report separates production runtimeClasspath packages from test/build tools.
OWASP `dependencyCheckAggregate` additionally scans the JVM dependency graph using
NVD and needs a valid NVD_API_KEY in the configured CI environment. An unavailable
feed is a failed scan, not a clean result. Suppress only verified non-applicable
findings with a recorded reason and expiry. Do not copy blanket suppressions from
old scan output. Checksums/lock updates do not substitute for behavioral tests.
