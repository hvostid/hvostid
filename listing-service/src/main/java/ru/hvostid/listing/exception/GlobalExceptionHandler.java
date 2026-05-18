package ru.hvostid.listing.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.hvostid.common.dto.ProblemDetails;
import ru.hvostid.common.exception.ConflictException;
import ru.hvostid.common.exception.ForbiddenException;
import ru.hvostid.common.exception.NotFoundException;
import ru.hvostid.common.exception.ProblemDetailsFactory;
import ru.hvostid.common.exception.UnauthorizedException;
import ru.hvostid.common.exception.ValidationException;

/**
 * Listing-service specific exception mappings. Bean Validation, AccessDenied, body parsing
 * and the generic fallback are handled by {@code common.exception.GlobalErrorHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ListingNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleNotFound(ListingNotFoundException ex, HttpServletRequest request) {
        log.debug("Listing not found: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.NOT_FOUND, NotFoundException.TYPE, "Listing not found", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetails> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.FORBIDDEN, ForbiddenException.TYPE, "Access denied", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidListingStatusException.class)
    public ResponseEntity<ProblemDetails> handleInvalidStatus(
            InvalidListingStatusException ex, HttpServletRequest request) {
        log.debug("Invalid listing status: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.BAD_REQUEST, ValidationException.TYPE, "Invalid listing status", ex.getMessage(), request);
    }

    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    public ResponseEntity<ProblemDetails> handleBadSort(RuntimeException ex, HttpServletRequest request) {
        // Triggered when a client supplies a sort/filter field that does not
        // map to an entity property (e.g. Swagger UI's default sort=["string"]).
        log.debug("Invalid query parameter: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.BAD_REQUEST, ValidationException.TYPE, "Invalid query parameter", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ProblemDetails> handleInvalidTransition(
            InvalidStatusTransitionException ex, HttpServletRequest request) {
        log.debug("Invalid status transition: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ValidationException.TYPE,
                "Invalid status transition",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetails> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        log.debug("Unauthorized: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.UNAUTHORIZED,
                ru.hvostid.common.exception.UnauthorizedException.TYPE,
                "Authentication required",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(DuplicateFlagException.class)
    public ResponseEntity<ProblemDetails> handleDuplicateFlag(DuplicateFlagException ex, HttpServletRequest request) {
        log.debug("Duplicate flag: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.CONFLICT, ConflictException.TYPE, "Duplicate flag", ex.getMessage(), request);
    }

    @ExceptionHandler(ListingNotFlaggableException.class)
    public ResponseEntity<ProblemDetails> handleListingNotFlaggable(
            ListingNotFlaggableException ex, HttpServletRequest request) {
        log.debug("Listing not flaggable: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.BAD_REQUEST, ValidationException.TYPE, "Listing not flaggable", ex.getMessage(), request);
    }

    @ExceptionHandler(FlagNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleFlagNotFound(FlagNotFoundException ex, HttpServletRequest request) {
        log.debug("Flag not found: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.NOT_FOUND, NotFoundException.TYPE, "Flag not found", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidFlagReviewException.class)
    public ResponseEntity<ProblemDetails> handleInvalidFlagReview(
            InvalidFlagReviewException ex, HttpServletRequest request) {
        log.debug("Invalid flag review: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.BAD_REQUEST, ValidationException.TYPE, "Invalid flag review", ex.getMessage(), request);
    }
}
