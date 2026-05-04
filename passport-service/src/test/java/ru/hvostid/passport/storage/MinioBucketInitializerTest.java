package ru.hvostid.passport.storage;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import ru.hvostid.passport.config.MinioProperties;

class MinioBucketInitializerTest {
    @Test
    void runCreatesAllConfiguredBuckets() {
        MinioStorageService storageService = org.mockito.Mockito.mock(MinioStorageService.class);
        MinioProperties properties = new MinioProperties(
                "http://localhost:9000",
                "minioadmin",
                "minioadmin",
                new MinioProperties.Buckets("pet-documents", "pet-photos"));
        MinioBucketInitializer initializer = new MinioBucketInitializer(properties, storageService);

        initializer.run(null);

        verify(storageService).ensureBucketExists("pet-documents");
        verify(storageService).ensureBucketExists("pet-photos");
    }
}
