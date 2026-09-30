# Restricted standalone MinIO image

Production and integration tests use this same build context. The image preserves
bucket CRUD, ordinary signed object PUT/GET/DELETE, S3 multipart upload, and
presigned PUT/GET with static credentials. Its tag is
`hvostid/minio:security-2026-09-30`; the test tag is
`hvostid/minio-test:security-2026-09-30`.

## Upstream boundary and reproducibility

The community [MinIO server](https://github.com/minio/minio) and
[client](https://github.com/minio/mc) repositories are archived. This is a locally
restricted build of their final source, with reviewed dependency updates; it is
not an upstream-supported security release and does not claim every upstream
advisory is fixed. Future findings require an explicit review or an image update.

| Input | Immutable version |
| --- | --- |
| Server | `7aac2a2c5b7c882e68c1ce017d8256be2feea27f` / `v0.0.0-20260212201848-7aac2a2c5b7c` |
| Client | `77f82e18b5401a65958f1619df6ebb994634bd88` / `v0.0.0-20251106162529-77f82e18b540` |
| Go builder | `1.26.8-alpine3.24@sha256:8ac98ca534ac3f51e1f420a1dd2c15e74c75cfa0f23f3ad27eb5d7236c349a0c` |
| Binary scanner | `golang.org/x/vuln/cmd/govulncheck@v1.1.4` |

The Dockerfile checks each source module's exact `h1` checksum before copying it.
Committed `server/go.mod`, `server/go.sum`, `mc/go.mod`, and `mc/go.sum` lock the
patched module graph. Builds use `-mod=readonly`, `GOTOOLCHAIN=local`, and
`go mod verify`; they never run an unversioned `go get` or toolchain download.
The exact-match `apply-patches.go` script fails when an upstream patch anchor
changes. Original AGPL license files remain at `/licenses/minio/LICENSE` and
`/licenses/mc/LICENSE`. Corresponding source is the two pinned public module
archives, the committed manifests, `apply-patches.go`, and `hardening.go`; the
Dockerfile gives the complete build procedure.

The dependency update includes x/crypto 0.56.0, x/net 0.58.0, compress 1.18.7,
jsonparser 1.1.2, go-jose/v4 4.1.4, Azure go-ntlmssp 0.1.1, edwards25519 1.1.1,
MQTT 1.5.1, AMQP 1.13.0, etcd client/pkg 3.5.33, gRPC 1.83.2,
OpenTelemetry SDK 1.44.0, Mongo driver 1.17.7, and Prometheus 0.311.3.
The manifests record all transitive versions, including graph differences
between server and client.

## Explicit restrictions

| Advisory | Restriction and evidence |
| --- | --- |
| [GHSA-hv4r-mvr4-25vw](https://github.com/minio/minio/security/advisories/GHSA-hv4r-mvr4-25vw), [GHSA-9c4q-hq6p-c237](https://github.com/minio/minio/security/advisories/GHSA-9c4q-hq6p-c237) | Reject `STREAMING-UNSIGNED-PAYLOAD-TRAILER` in headers or query before authentication/body parsing, following the upstream workaround. Ordinary signed payload and presigned requests remain supported. |
| [GHSA-h749-fxx7-pwpg](https://github.com/minio/minio/security/advisories/GHSA-h749-fxx7-pwpg) | S3 Select query rejected; its handler registration removed. |
| [GHSA-3rh2-v3gr-35p9](https://github.com/minio/minio/security/advisories/GHSA-3rh2-v3gr-35p9) | Shared metadata extractor rejects all replication metadata keys from headers, query, POST forms, and Snowball TAR PAX records. POST-policy uploads are disabled. All bucket/site/batch replication and remote-target setup are disabled, including loading targets from existing disk configuration and starting target heartbeat/reload goroutines. |
| [GHSA-xh8f-g2qw-gcm7](https://github.com/minio/minio/security/advisories/GHSA-xh8f-g2qw-gcm7) | Distributed deployment is refused; internode storage paths are blocked. Only standalone storage is supported. |
| [GHSA-jv87-32hw-hh99](https://github.com/minio/minio/security/advisories/GHSA-jv87-32hw-hh99), [GHSA-5cx5-wh4m-82fh](https://github.com/minio/minio/security/advisories/GHSA-5cx5-wh4m-82fh) | STS routes and LDAP/OIDC federation are unavailable. Legacy temporary credentials are rejected using upstream credential classification; security-token headers/query are blocked. Static credentials are required. |
| [GO-2025-3495](https://pkg.go.dev/vuln/GO-2025-3495) / [GHSA-wc79-7x8x-2p58](https://github.com/minio/minio/security/advisories/GHSA-wc79-7x8x-2p58) | FTP/SFTP startup flags are rejected. Go DB cannot map the nonstandard release tags and reports an open SEMVER range; its documented custom range ends at `RELEASE.2025-02-28T09-55-16Z`. We record the finding as not exposed, rather than silently excluding a claimed fixed version. |

The bundled web console is disabled (`MINIO_BROWSER=off`); Compose exposes only
S3 port 9000. Console/federation, POST-policy upload, FTP/SFTP, distributed
deployment, S3 Select, and replication are unsupported. These restrictions do
not repair objects corrupted before an upgrade or undo a prior compromise.
For an existing installation that enabled affected features, review stored data,
remove obsolete federation/replication configuration, and rotate compromised
credentials before resuming service. Persisted temporary credentials are not
accepted by this image even if their original expiration has not passed.

## Repeatable checks

```sh
node --test docker/minio/audit-upstream.test.mjs
node docker/minio/audit-upstream.mjs
docker buildx build --target audit-reports --no-cache-filter audit \
  --output type=local,dest=build/reports/minio-binary docker/minio
docker build -t hvostid/minio:security-2026-09-30 docker/minio
node docker/minio/security-smoke.mjs
```

The main-module audit uses GitHub repository advisories and OSV. It checks exact
reviewed advisory fingerprints, missing/new advisories, contradictory affected
versions, input pins/checksums, and failed lookups. `GH_TOKEN` is optional and
increases the GitHub API allowance. A nonzero exit blocks promotion. Its report
defaults to `build/reports/minio-upstream.json` (`MINIO_AUDIT_REPORT` override).
The manifest deliberately records individual mitigated/not-exposed advisories;
there is no blanket MinIO module exclusion.

`govulncheck` audits the actual server/client binaries against the current Go
vulnerability database. CI bypasses the `audit` stage cache on each security
run; successful reports and `go version -m` inventories are exported by the
scratch `audit-reports` target. Failures print their reports to the build log.
Local patched main modules identify as `(devel)`, so binary scanning alone
cannot version-match upstream MinIO advisories: the separate main-module audit
is mandatory. On 2026-09-30, both patched binaries had zero symbol findings and
zero imported-package findings. The unused, unlinked x/crypto/openpgp package
still has module-only [GO-2026-5932](https://pkg.go.dev/vuln/GO-2026-5932); this is
explicitly distinct from a claim that the entire dependency graph has no
advisories.

The smoke script creates/removes its own Docker container, with random test
credentials, a loopback ephemeral port, and tmpfs data. It checks 1/5/10 MiB
round trips, a two-part 10 MiB multipart upload, presigned requests, signed
POST/query/PAX rejection, ordinary Snowball metadata, restricted HTTP paths,
FTP/SFTP startup rejection, and absence of the console. `MINIO_IMAGE` selects
the image; `MINIO_SMOKE_REPORT` overrides `build/reports/minio-smoke.json`.
It never uses deployment credentials or production volumes.
