package ru.hvostid.passport;

import org.testcontainers.utility.DockerImageName;

/**
 * Resolves the Redis Docker image for Testcontainers-backed tests from the
 * {@code testcontainers.redis.image} system property. The root
 * {@code build.gradle.kts} injects the property from
 * {@code libs.versions.toml#redis-image} so the version stays in sync with
 * the other test image pins and dependabot can bump it.
 */
public final class RedisTestImage {
    private static final String IMAGE_PROPERTY = "testcontainers.redis.image";

    private RedisTestImage() {}

    public static DockerImageName resolve() {
        String image = System.getProperty(IMAGE_PROPERTY);
        if (image == null || image.isBlank()) {
            throw new IllegalStateException("System property '" + IMAGE_PROPERTY + "' is not set. "
                    + "Run tests via Gradle so the image is injected from libs.versions.toml.");
        }
        return DockerImageName.parse(image);
    }
}
