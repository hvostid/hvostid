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

    private static final String MIME_IMAGE_JPEG = "image/jpeg";
    private static final String MIME_IMAGE_PNG = "image/png";
    private static final String MIME_APPLICATION_PDF = "application/pdf";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(MIME_IMAGE_JPEG, MIME_IMAGE_PNG, MIME_APPLICATION_PDF);
    private static final Map<String, Set<String>> MIME_TYPES_BY_EXTENSION = Map.of(
            "jpg", Set.of(MIME_IMAGE_JPEG),
            "jpeg", Set.of(MIME_IMAGE_JPEG),
            "png", Set.of(MIME_IMAGE_PNG),
            "pdf", Set.of(MIME_APPLICATION_PDF));

    public void validate(MultipartFile file) {
        validate(file, null);
    }

    public void validate(MultipartFile file, ru.hvostid.passport.entity.PassportDocumentType type) {
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

        if (originalFilename.length() > 255 || originalFilename.contains("/") || originalFilename.contains("\\"))
            throw new InvalidPassportDocumentException("Invalid file name");
        if (type == ru.hvostid.passport.entity.PassportDocumentType.PHOTO && MIME_APPLICATION_PDF.equals(contentType))
            throw new UnsupportedPassportDocumentException("PHOTO requires a JPEG or PNG image");
        try {
            if (MIME_APPLICATION_PDF.equals(contentType)) {
                try (var pdf = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
                    if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 100)
                        throw new InvalidPassportDocumentException(
                                "PDF must be unencrypted and contain 1 to 100 pages");
                    var catalog = pdf.getDocumentCatalog();
                    var names = catalog.getCOSObject().getCOSDictionary(org.apache.pdfbox.cos.COSName.NAMES);
                    if (catalog.getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.OPEN_ACTION)
                            || catalog.getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.AA)
                            || catalog.getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.ACRO_FORM)
                            || names != null
                                    && (names.containsKey(org.apache.pdfbox.cos.COSName.JAVA_SCRIPT)
                                            || names.containsKey(org.apache.pdfbox.cos.COSName.EMBEDDED_FILES)))
                        throw new InvalidPassportDocumentException("PDF actions and attachments are not supported");
                    for (var page : pdf.getPages()) {
                        if (page.getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.AA))
                            throw new InvalidPassportDocumentException("PDF actions are not supported");
                        for (var annotation : page.getAnnotations()) {
                            var dictionary = annotation.getCOSObject();
                            if (dictionary.containsKey(org.apache.pdfbox.cos.COSName.A)
                                    || dictionary.containsKey(org.apache.pdfbox.cos.COSName.AA)
                                    || "FileAttachment".equals(annotation.getSubtype())
                                    || "RichMedia".equals(annotation.getSubtype()))
                                throw new InvalidPassportDocumentException(
                                        "PDF actions and attachments are not supported");
                        }
                    }
                }
            } else {
                try (var input = javax.imageio.ImageIO.createImageInputStream(file.getInputStream())) {
                    var readers = javax.imageio.ImageIO.getImageReaders(input);
                    if (!readers.hasNext())
                        throw new InvalidPassportDocumentException("Image content cannot be decoded");
                    var reader = readers.next();
                    try {
                        reader.setInput(input, true, true);
                        String format = reader.getFormatName();
                        if (!(MIME_IMAGE_PNG.equals(contentType) && format.equalsIgnoreCase("png")
                                || MIME_IMAGE_JPEG.equals(contentType) && format.equalsIgnoreCase("jpeg")))
                            throw new InvalidPassportDocumentException("Image bytes do not match content type");
                        long width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width <= 0 || height <= 0 || width * height > 25_000_000L)
                            throw new InvalidPassportDocumentException("Image exceeds 25 megapixels");
                        if (reader.read(0) == null) throw new InvalidPassportDocumentException("Invalid image content");
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (java.io.IOException ex) {
            throw new InvalidPassportDocumentException("Invalid document content", ex);
        }
    }
}
