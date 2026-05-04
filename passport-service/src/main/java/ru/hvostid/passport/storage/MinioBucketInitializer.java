package ru.hvostid.passport.storage;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.hvostid.passport.config.MinioProperties;

@Component
public class MinioBucketInitializer implements ApplicationRunner {
    private final MinioProperties properties;
    private final MinioStorageService storageService;

    public MinioBucketInitializer(MinioProperties properties, MinioStorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List.of(properties.buckets().documents(), properties.buckets().photos())
                .forEach(storageService::ensureBucketExists);
    }
}
