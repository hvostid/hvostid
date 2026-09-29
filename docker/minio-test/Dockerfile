# MinIO no longer publishes community images. Build the same release from
# its immutable upstream commit instead of relying on a registry mirror.
FROM golang:1.24.7-alpine3.22@sha256:fc2cff6625f3c1c92e6c85938ac5bd09034ad0d4bc2dfb08278020b68540dbb5 AS build

# RELEASE.2025-09-07T16-13-09Z
RUN CGO_ENABLED=0 GOBIN=/out go install github.com/minio/minio@07c3a429bfed433e49018cb0f78a52145d4bedeb

FROM alpine:3.22.6
RUN apk add --no-cache ca-certificates
COPY --from=build /out/minio /usr/local/bin/minio
COPY --from=build /go/pkg/mod/github.com/minio/minio@*/LICENSE /licenses/minio/LICENSE

LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.revision="07c3a429bfed433e49018cb0f78a52145d4bedeb" \
      org.opencontainers.image.licenses="AGPL-3.0-only"

EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
