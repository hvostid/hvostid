package ru.hvostid.passport.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class MinioStorageServiceTest {
    private static final String BUCKET = "pet-documents";

    @Container
    static final MinIOContainer minio =
            new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2024-01-16T16-07-38Z"));

    private MinioClient minioClient;
    private MinioStorageService storageService;

    @BeforeEach
    void setUp() {
        minioClient = MinioClient.builder()
                .endpoint(minio.getS3URL())
                .credentials(minio.getUserName(), minio.getPassword())
                .build();
        storageService = new MinioStorageService(minioClient);
    }

    @Test
    void ensureBucketExistsCreatesMissingBucket() throws Exception {
        storageService.ensureBucketExists(BUCKET);

        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(BUCKET).build());
        assertThat(exists).isTrue();
    }

    @Test
    void uploadDownloadPresignAndDeleteObject() throws Exception {
        storageService.ensureBucketExists(BUCKET);
        String objectName = "seller-id/passport-id/document.txt";
        byte[] content = "passport document".getBytes(StandardCharsets.UTF_8);

        try (ByteArrayInputStream payload = new ByteArrayInputStream(content)) {
            storageService.upload(BUCKET, objectName, payload, content.length, "text/plain");
        }
        String downloaded;
        try (var stream = storageService.download(BUCKET, objectName)) {
            downloaded = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String presignedUrl = storageService.getPresignedUrl(BUCKET, objectName, Duration.ofMinutes(5));
        storageService.delete(BUCKET, objectName);

        assertThat(downloaded).isEqualTo("passport document");
        assertThat(presignedUrl).contains(objectName).contains("X-Amz-Expires=300");
        assertThatThrownBy(() -> storageService.download(BUCKET, objectName)).isInstanceOf(MinioStorageException.class);
    }

    @Test
    void getPresignedUrlRejectsInvalidExpiry() {
        assertThatThrownBy(() -> storageService.getPresignedUrl(BUCKET, "object.txt", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiry must be positive");
        assertThatThrownBy(() -> storageService.getPresignedUrl(BUCKET, "object.txt", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiry must be positive");
        assertThatThrownBy(() -> storageService.getPresignedUrl(BUCKET, "object.txt", Duration.ofDays(8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiry must not exceed 7 days");
    }
}
