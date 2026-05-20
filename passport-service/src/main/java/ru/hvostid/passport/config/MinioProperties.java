package ru.hvostid.passport.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import ru.hvostid.passport.entity.PassportDocumentType;

@Validated
@ConfigurationProperties(prefix = "hvostid.minio")
public record MinioProperties(
        @NotBlank String url,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @Valid Buckets buckets) {
    public record Buckets(
            @NotBlank String documents, @NotBlank String photos) {
        public String forDocumentType(PassportDocumentType type) {
            return switch (type) {
                case PHOTO -> photos;
                case VACCINATION_CERT, VET_RECORD, OTHER -> documents;
            };
        }
    }
}
