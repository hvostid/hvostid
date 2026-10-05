# MinIO integration-test image

Integration tests build the same restricted, dependency-patched image used in
production. Its only build context is [`../minio`](../minio/README.md); this
directory intentionally has no separate Dockerfile.

```sh
./gradlew :passport-service:prepareMinioTestImage
```

The task builds `hvostid/minio-test:security-2026-09-30` before passport tests.
The tag is pinned in `gradle/libs.versions.toml`, and Testcontainers declares
compatibility with `minio/minio`. Docker must be running. Source, toolchain,
dependency checksums, restrictions, licenses, and audit/smoke commands are
documented in the canonical image README. Context changes invalidate the test
image build and trigger the passport CI job.
