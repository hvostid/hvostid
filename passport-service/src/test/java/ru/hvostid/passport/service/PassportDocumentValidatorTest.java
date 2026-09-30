package ru.hvostid.passport.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.hvostid.passport.exception.InvalidPassportDocumentException;
import ru.hvostid.passport.exception.PassportDocumentTooLargeException;
import ru.hvostid.passport.exception.UnsupportedPassportDocumentException;

class PassportDocumentValidatorTest {
    private final PassportDocumentValidator validator = new PassportDocumentValidator();

    @Test
    void validateAcceptsSupportedFormats() {
        assertThatCode(() -> validator.validate(file("photo.jpg", "image/jpeg")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("photo.png", "image/png"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("record.pdf", "application/pdf")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(InvalidPassportDocumentException.class);
    }

    @Test
    void validateRejectsTooLargeFile() {
        byte[] content = new byte[(int) PassportDocumentValidator.MAX_FILE_SIZE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(PassportDocumentTooLargeException.class);
    }

    @Test
    void validateRejectsUnsupportedExtension() {
        MockMultipartFile zip = file("archive.zip", "application/pdf");
        assertThatThrownBy(() -> validator.validate(zip)).isInstanceOf(UnsupportedPassportDocumentException.class);
    }

    @Test
    void validateRejectsUnsupportedContentType() {
        MockMultipartFile textJpg = file("photo.jpg", "text/plain");
        assertThatThrownBy(() -> validator.validate(textJpg)).isInstanceOf(UnsupportedPassportDocumentException.class);
    }

    @Test
    void validateRejectsExtensionAndContentTypeMismatch() {
        MockMultipartFile mismatched = file("photo.jpg", "application/pdf");
        assertThatThrownBy(() -> validator.validate(mismatched))
                .isInstanceOf(UnsupportedPassportDocumentException.class);
    }

    @Test
    void rejectsForgedBytesAndPdfPhotos() {
        var forged = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "not-an-image".getBytes());
        assertThatThrownBy(() -> validator.validate(forged)).isInstanceOf(InvalidPassportDocumentException.class);
        assertThatThrownBy(() -> validator.validate(
                        file("photo.pdf", "application/pdf"), ru.hvostid.passport.entity.PassportDocumentType.PHOTO))
                .isInstanceOf(UnsupportedPassportDocumentException.class);
        var mismatch = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", ru.hvostid.passport.TestDocumentContent.image("png"));
        assertThatThrownBy(() -> validator.validate(mismatch)).isInstanceOf(InvalidPassportDocumentException.class);
    }

    private MockMultipartFile file(String filename, String contentType) {
        return new MockMultipartFile(
                "file",
                filename,
                contentType,
                contentType.equals("application/pdf")
                        ? ru.hvostid.passport.TestDocumentContent.pdf()
                        : ru.hvostid.passport.TestDocumentContent.image(
                                contentType.equals("image/png") ? "png" : "jpg"));
    }
}
