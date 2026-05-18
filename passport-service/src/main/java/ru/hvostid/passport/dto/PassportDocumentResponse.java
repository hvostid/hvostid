package ru.hvostid.passport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.entity.PassportDocumentType;

@Schema(description = "Passport document metadata")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PassportDocumentResponse(
        Long id,
        PassportDocumentType type,
        String originalFilename,
        String mimeType,
        long size,
        Instant uploadedAt,

        @Schema(
                description = "Short-TTL presigned MinIO URL the caller can use directly (e.g. as an <img src>). "
                        + "Null when the caller does not have download access for this document.")
        String downloadUrl) {
    public static PassportDocumentResponse from(PassportDocument document) {
        return from(document, null);
    }

    public static PassportDocumentResponse from(PassportDocument document, String downloadUrl) {
        return new PassportDocumentResponse(
                document.getId(),
                document.getType(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getSize(),
                document.getUploadedAt(),
                downloadUrl);
    }
}
