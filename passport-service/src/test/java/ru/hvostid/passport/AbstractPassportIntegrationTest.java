package ru.hvostid.passport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;

public abstract class AbstractPassportIntegrationTest extends AbstractPostgresContainerTest {
    private static final int REDIS_PORT = 6379;

    private static final MinIOContainer minio = new MinIOContainer(MinioTestImage.resolve());
    private static final GenericContainer<?> redis = new GenericContainer<>(RedisTestImage.resolve())
            .withCommand("redis-server", "--appendonly", "no", "--save", "")
            .withExposedPorts(REDIS_PORT);

    static {
        minio.start();
        redis.start();
    }

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("hvostid.minio.url", minio::getS3URL);
        registry.add("hvostid.minio.access-key", minio::getUserName);
        registry.add("hvostid.minio.secret-key", minio::getPassword);
        registry.add("hvostid.minio.buckets.documents", () -> "pet-documents");
        registry.add("hvostid.minio.buckets.photos", () -> "pet-photos");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
    }
}
