package ru.hvostid.passport.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.hvostid.common.dto.ProblemDetails;
import ru.hvostid.common.exception.ForbiddenException;
import ru.hvostid.common.exception.NotFoundException;
import ru.hvostid.common.exception.ProblemDetailsFactory;
import ru.hvostid.common.exception.ValidationException;

/**
 * Passport-service specific exception mappings. Bean Validation, AccessDenied, body
 * parsing and the generic fallback are handled by {@code common.exception.GlobalErrorHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UPLOAD_TYPE = "urn:problem-type:upload-too-large";
    private static final String UNSUPPORTED_TYPE = "urn:problem-type:unsupported-media-type";
    private static final String UPSTREAM_TYPE = "urn:problem-type:upstream-unavailable";

    @ExceptionHandler(PassportNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleNotFound(PassportNotFoundException ex, HttpServletRequest request) {
        log.debug("Passport not found: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.NOT_FOUND, NotFoundException.TYPE, "Passport not found", ex.getMessage(), request);
    }

    @ExceptionHandler(ListingServiceUnavailableException.class)
    public ResponseEntity<ProblemDetails> handleListingServiceUnavailable(
            ListingServiceUnavailableException ex, HttpServletRequest request) {
        log.warn("Listing service unavailable: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.SERVICE_UNAVAILABLE, UPSTREAM_TYPE, "Listing service unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler(PassportDocumentNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleDocumentNotFound(
            PassportDocumentNotFoundException ex, HttpServletRequest request) {
        log.debug("Passport document not found: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.NOT_FOUND, NotFoundException.TYPE, "Passport document not found", ex.getMessage(), request);
    }

    @ExceptionHandler(PassportAccessDeniedException.class)
    public ResponseEntity<ProblemDetails> handleAccessDenied(
            PassportAccessDeniedException ex, HttpServletRequest request) {
        log.warn("Passport access denied: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.FORBIDDEN, ForbiddenException.TYPE, "Access denied", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidPassportDocumentException.class)
    public ResponseEntity<ProblemDetails> handleInvalidDocument(
            InvalidPassportDocumentException ex, HttpServletRequest request) {
        log.debug("Invalid passport document: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.BAD_REQUEST,
                ValidationException.TYPE,
                "Invalid passport document",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler({PassportDocumentTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ProblemDetails> handleDocumentTooLarge(Exception ex, HttpServletRequest request) {
        log.debug("Passport document too large: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.CONTENT_TOO_LARGE,
                UPLOAD_TYPE,
                "Upload too large",
                "Document file must not exceed 10 MB",
                request);
    }

    @ExceptionHandler(UnsupportedPassportDocumentException.class)
    public ResponseEntity<ProblemDetails> handleUnsupportedDocument(
            UnsupportedPassportDocumentException ex, HttpServletRequest request) {
        log.debug("Unsupported passport document: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                UNSUPPORTED_TYPE,
                "Unsupported document type",
                ex.getMessage(),
                request);
    }
}
