package ru.hvostid.passport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.entity.PassportDocumentType;

@Schema(description = "Passport document metadata")
public record PassportDocumentResponse(
        Long id, PassportDocumentType type, String originalFilename, String mimeType, long size, Instant uploadedAt) {
    public static PassportDocumentResponse from(PassportDocument document) {
        return new PassportDocumentResponse(
                document.getId(),
                document.getType(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getSize(),
                document.getUploadedAt());
    }
}
