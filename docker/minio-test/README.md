# MinIO integration-test image

MinIO's community images are no longer available from Docker Hub or Quay.
This Dockerfile compiles the previously tested release directly from upstream:

- Release: `RELEASE.2025-09-07T16-13-09Z`
- Commit: `07c3a429bfed433e49018cb0f78a52145d4bedeb`
- Source: https://github.com/minio/minio/tree/07c3a429bfed433e49018cb0f78a52145d4bedeb
- License: AGPL-3.0; the upstream license is included at `/licenses/minio/LICENSE`.

`:passport-service:prepareMinioTestImage` builds the image before passport tests.
Run `./gradlew check` or `./gradlew build` as usual with Docker running. The first
build downloads the Go toolchain image and upstream modules; subsequent builds
reuse Docker's layer cache. No local image override or registry login is needed.

The local image name is configured by `minio-image` in
`gradle/libs.versions.toml`. Testcontainers marks it as compatible with
`minio/minio` so the standard MinIO fixture can use it. Changes to this build
context invalidate the passport test task and trigger the corresponding CI job.

To build just the image:

```sh
./gradlew :passport-service:prepareMinioTestImage
```
