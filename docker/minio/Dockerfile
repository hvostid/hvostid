# syntax=docker/dockerfile:1
# See README.md: archived upstream, pinned dependencies, and local protocol guards.
FROM golang:1.26.8-alpine3.24@sha256:8ac98ca534ac3f51e1f420a1dd2c15e74c75cfa0f23f3ad27eb5d7236c349a0c AS toolchain
ENV CGO_ENABLED=0 GOTOOLCHAIN=local GOMAXPROCS=2 GOMEMLIMIT=640MiB GOFLAGS=-p=2

FROM toolchain AS server-source
WORKDIR /src/server
RUN --mount=type=cache,target=/go/pkg/mod,sharing=locked \
    go mod download -json github.com/minio/minio@v0.0.0-20260212201848-7aac2a2c5b7c > /tmp/server-source.json && \
    grep -F '"Sum": "h1:/qdoYujxF5MBLDMVLeiYJ/7fBa1HikX39Dvzb9Ji8pA="' /tmp/server-source.json && \
    cp -R /go/pkg/mod/github.com/minio/minio@v0.0.0-20260212201848-7aac2a2c5b7c/. . && chmod -R u+w .
COPY server/go.mod server/go.sum ./
COPY apply-patches.go hardening.go hardening_test.go /patches/
RUN go run /patches/apply-patches.go /src/server && \
    cp /patches/hardening.go cmd/hvostid_security.go && \
    go test /patches/hardening.go /patches/hardening_test.go

FROM server-source AS server-build
RUN --mount=type=cache,target=/go/pkg/mod,sharing=locked \
    --mount=type=cache,target=/root/.cache/go-build,sharing=locked \
    go build -mod=readonly -trimpath -buildvcs=false -o /out/minio . && go mod verify

FROM toolchain AS client-build
WORKDIR /src/mc
RUN --mount=type=cache,target=/go/pkg/mod,sharing=locked \
    go mod download -json github.com/minio/mc@v0.0.0-20251106162529-77f82e18b540 > /tmp/client-source.json && \
    grep -F '"Sum": "h1:OAeamQLGQyf7sT/JEocLpAfMTU2Me5Jx3c1MjyS/mNo="' /tmp/client-source.json && \
    cp -R /go/pkg/mod/github.com/minio/mc@v0.0.0-20251106162529-77f82e18b540/. . && chmod -R u+w .
COPY mc/go.mod mc/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod,sharing=locked \
    --mount=type=cache,target=/root/.cache/go-build,sharing=locked \
    go build -mod=readonly -trimpath -buildvcs=false -o /out/mc . && go mod verify

FROM toolchain AS audit-tools
RUN --mount=type=cache,target=/go/pkg/mod,sharing=locked \
    --mount=type=cache,target=/root/.cache/go-build,sharing=locked \
    GOBIN=/tools go install golang.org/x/vuln/cmd/govulncheck@v1.1.4

# CI refreshes this stage with --no-cache-filter audit. Any symbol finding fails.
# Main-module advisories need the separate audit-upstream.mjs check: local builds
# identify the main module as (devel), which govulncheck cannot version-match.
FROM audit-tools AS audit
COPY --from=server-build /out/minio /out/minio
COPY --from=client-build /out/mc /out/mc
RUN mkdir -p /reports && \
    (/tools/govulncheck -mode=binary -show=verbose /out/minio > /reports/minio.txt 2>&1 || { cat /reports/minio.txt; exit 1; }) && \
    (/tools/govulncheck -mode=binary -show=verbose /out/mc > /reports/mc.txt 2>&1 || { cat /reports/mc.txt; exit 1; }) && \
    go version -m /out/minio > /reports/minio-build.txt && \
    go version -m /out/mc > /reports/mc-build.txt

FROM scratch AS audit-reports
COPY --from=audit /reports/ /

FROM alpine:3.22.6@sha256:5291449c3df73caf6ed85e649dec1b9e818b39a5d8c871e97afc13e9cd5e8fa8 AS runtime
RUN apk add --no-cache ca-certificates
ENV MINIO_BROWSER=off
COPY --from=audit /out/minio /out/mc /usr/local/bin/
COPY --from=server-source /src/server/LICENSE /licenses/minio/LICENSE
COPY --from=client-build /src/mc/LICENSE /licenses/mc/LICENSE
LABEL org.opencontainers.image.source="https://github.com/hvostid/hvostid" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.description="Locally hardened standalone MinIO, archived upstream with pinned dependency patches" \
      io.hvostid.minio.revision="7aac2a2c5b7c882e68c1ce017d8256be2feea27f" \
      io.hvostid.mc.revision="77f82e18b5401a65958f1619df6ebb994634bd88"
EXPOSE 9000
HEALTHCHECK --interval=5s --timeout=3s --retries=10 CMD wget -qO- http://localhost:9000/minio/health/live || exit 1
ENTRYPOINT ["minio"]
CMD ["server", "/data"]
