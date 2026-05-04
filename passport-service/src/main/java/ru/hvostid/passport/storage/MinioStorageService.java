package ru.hvostid.passport.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class MinioStorageService {
    private static final long PART_SIZE = 10L * 1024L * 1024L;
    private static final Duration MAX_PRESIGNED_URL_EXPIRY = Duration.ofDays(7);

    private final MinioClient minioClient;

    public MinioStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new MinioStorageException("Failed to ensure MinIO bucket exists: " + bucket, ex);
        }
    }

    public void upload(String bucket, String objectName, InputStream inputStream, long objectSize, String contentType) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        if (objectSize < 0) {
            throw new IllegalArgumentException("objectSize must not be negative");
        }
        try {
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(objectName).stream(inputStream, objectSize, PART_SIZE)
                            .contentType(contentType)
                            .build());
        } catch (Exception ex) {
            throw new MinioStorageException("Failed to upload MinIO object: " + objectName, ex);
        }
    }

    /**
     * Returns an open stream backed by the MinIO HTTP response. Callers must close the returned stream
     * after reading it to release the underlying connection.
     */
    public InputStream download(String bucket, String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception ex) {
            throw new MinioStorageException("Failed to download MinIO object: " + objectName, ex);
        }
    }

    public String getPresignedUrl(String bucket, String objectName, Duration expiry) {
        validatePresignedUrlExpiry(expiry);
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry(Math.toIntExact(expiry.toSeconds()), TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new MinioStorageException("Failed to create MinIO presigned URL: " + objectName, ex);
        }
    }

    private void validatePresignedUrlExpiry(Duration expiry) {
        Objects.requireNonNull(expiry, "expiry must not be null");
        if (expiry.isZero() || expiry.isNegative()) {
            throw new IllegalArgumentException("expiry must be positive");
        }
        if (expiry.compareTo(MAX_PRESIGNED_URL_EXPIRY) > 0) {
            throw new IllegalArgumentException("expiry must not exceed 7 days");
        }
    }

    public void delete(String bucket, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception ex) {
            throw new MinioStorageException("Failed to delete MinIO object: " + objectName, ex);
        }
    }
}
