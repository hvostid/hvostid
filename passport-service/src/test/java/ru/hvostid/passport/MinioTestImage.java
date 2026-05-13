package ru.hvostid.passport;

import org.testcontainers.utility.DockerImageName;

/**
 * Resolves the MinIO Docker image to use in Testcontainers-backed tests
 * from the {@code testcontainers.minio.image} system property. The root
 * {@code build.gradle.kts} injects the property from
 * {@code libs.versions.toml#minio-image} so the version stays in sync
 * with the {@code postgres-image} pin and dependabot can bump it.
 */
public final class MinioTestImage {
    private static final String IMAGE_PROPERTY = "testcontainers.minio.image";

    private MinioTestImage() {}

    public static DockerImageName resolve() {
        String image = System.getProperty(IMAGE_PROPERTY);
        if (image == null || image.isBlank()) {
            throw new IllegalStateException("System property '" + IMAGE_PROPERTY + "' is not set. "
                    + "Run tests via Gradle so the image is injected from libs.versions.toml.");
        }
        return DockerImageName.parse(image);
    }
}
