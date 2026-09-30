# Community MinIO and mc are built from immutable upstream source revisions.
FROM golang:1.24.7-alpine3.22@sha256:fc2cff6625f3c1c92e6c85938ac5bd09034ad0d4bc2dfb08278020b68540dbb5 AS build
# RELEASE.2025-09-07T16-13-09Z and mc RELEASE.2025-08-13T08-35-41Z
RUN CGO_ENABLED=0 GOBIN=/out go install github.com/minio/minio@07c3a429bfed433e49018cb0f78a52145d4bedeb
RUN CGO_ENABLED=0 GOBIN=/out go install github.com/minio/mc@7394ce0dd2a80935aded936b09fa12cbb3cb8096

FROM alpine:3.22.6@sha256:5291449c3df73caf6ed85e649dec1b9e818b39a5d8c871e97afc13e9cd5e8fa8
RUN apk add --no-cache ca-certificates
COPY --from=build /out/minio /out/mc /usr/local/bin/
COPY --from=build /go/pkg/mod/github.com/minio/minio@*/LICENSE /licenses/minio/LICENSE
COPY --from=build /go/pkg/mod/github.com/minio/mc@*/LICENSE /licenses/mc/LICENSE
LABEL org.opencontainers.image.source="https://github.com/hvostid/hvostid" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.description="Pinned MinIO server and client, built from upstream source"
EXPOSE 9000 9001
HEALTHCHECK --interval=5s --timeout=3s --retries=10 CMD wget -qO- http://localhost:9000/minio/health/live || exit 1
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
