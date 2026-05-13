package ru.hvostid.passport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;

@Testcontainers
public abstract class AbstractPassportIntegrationTest extends AbstractPostgresContainerTest {
    @Container
    static final MinIOContainer minio = new MinIOContainer(MinioTestImage.resolve());

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("hvostid.minio.url", minio::getS3URL);
        registry.add("hvostid.minio.access-key", minio::getUserName);
        registry.add("hvostid.minio.secret-key", minio::getPassword);
        registry.add("hvostid.minio.buckets.documents", () -> "pet-documents");
        registry.add("hvostid.minio.buckets.photos", () -> "pet-photos");
    }
}
