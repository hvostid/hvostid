package ru.hvostid.passport.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import ru.hvostid.passport.exception.InvalidPassportDocumentException;
import ru.hvostid.passport.exception.PassportDocumentTooLargeException;
import ru.hvostid.passport.exception.UnsupportedPassportDocumentException;

@Component
public class PassportDocumentValidator {
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final Map<String, Set<String>> MIME_TYPES_BY_EXTENSION = Map.of(
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"),
            "pdf", Set.of("application/pdf"));

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidPassportDocumentException("Document file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new PassportDocumentTooLargeException("Document file must not exceed 10 MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null || extension.isBlank()) {
            throw new UnsupportedPassportDocumentException("Document file extension is not supported");
        }

        String normalizedExtension = extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(normalizedExtension)) {
            throw new UnsupportedPassportDocumentException("Document file extension is not supported");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new UnsupportedPassportDocumentException("Document content type is not supported");
        }
        if (!MIME_TYPES_BY_EXTENSION.get(normalizedExtension).contains(contentType)) {
            throw new UnsupportedPassportDocumentException("Document extension does not match content type");
        }
    }
}
