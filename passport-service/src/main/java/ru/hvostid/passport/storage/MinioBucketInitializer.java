package ru.hvostid.passport.storage;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.hvostid.passport.config.MinioProperties;

@Component
public class MinioBucketInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MinioBucketInitializer.class);

    private final MinioProperties properties;
    private final MinioStorageService storageService;

    public MinioBucketInitializer(MinioProperties properties, MinioStorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> buckets =
                List.of(properties.buckets().documents(), properties.buckets().photos());
        log.info("Ensuring MinIO buckets exist, bucketCount={}", buckets.size());
        buckets.forEach(this::ensureBucketExists);
    }

    private void ensureBucketExists(String bucket) {
        storageService.ensureBucketExists(bucket);
        log.info("MinIO bucket is ready, bucket={}", bucket);
    }
}
