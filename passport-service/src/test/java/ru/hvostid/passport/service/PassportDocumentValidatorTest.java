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
        assertThatThrownBy(() -> validator.validate(file("archive.zip", "application/pdf")))
                .isInstanceOf(UnsupportedPassportDocumentException.class);
    }

    @Test
    void validateRejectsUnsupportedContentType() {
        assertThatThrownBy(() -> validator.validate(file("photo.jpg", "text/plain")))
                .isInstanceOf(UnsupportedPassportDocumentException.class);
    }

    @Test
    void validateRejectsExtensionAndContentTypeMismatch() {
        assertThatThrownBy(() -> validator.validate(file("photo.jpg", "application/pdf")))
                .isInstanceOf(UnsupportedPassportDocumentException.class);
    }

    private MockMultipartFile file(String filename, String contentType) {
        return new MockMultipartFile("file", filename, contentType, "content".getBytes());
    }
}
