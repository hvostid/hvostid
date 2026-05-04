package ru.hvostid.passport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;

@SpringBootTest
@Testcontainers
class PassportServiceApplicationTest extends AbstractPostgresContainerTest {
    @Container
    static final MinIOContainer minio =
            new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2024-01-16T16-07-38Z"));

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("hvostid.minio.url", minio::getS3URL);
        registry.add("hvostid.minio.access-key", minio::getUserName);
        registry.add("hvostid.minio.secret-key", minio::getPassword);
        registry.add("hvostid.minio.buckets.documents", () -> "pet-documents");
        registry.add("hvostid.minio.buckets.photos", () -> "pet-photos");
    }

    @Test
    void contextLoads() {}
}
