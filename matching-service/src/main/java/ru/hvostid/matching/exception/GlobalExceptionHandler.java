package ru.hvostid.matching.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.hvostid.common.dto.ProblemDetails;
import ru.hvostid.common.exception.NotFoundException;
import ru.hvostid.common.exception.ProblemDetailsFactory;
import ru.hvostid.common.exception.ValidationException;

/**
 * Matching-service specific exception mappings. Bean Validation, AccessDenied, body
 * parsing and the generic fallback are handled by {@code common.exception.GlobalErrorHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UPSTREAM_TYPE = "https://hvostid.example/errors/upstream-unavailable";

    @ExceptionHandler(QuestionnaireNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleNotFound(
            QuestionnaireNotFoundException ex, HttpServletRequest request) {
        log.debug("Questionnaire not found: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.NOT_FOUND, NotFoundException.TYPE, "Questionnaire not found", ex.getMessage(), request);
    }

    @ExceptionHandler(QuestionnaireRequiredException.class)
    public ResponseEntity<ProblemDetails> handleQuestionnaireRequired(
            QuestionnaireRequiredException ex, HttpServletRequest request) {
        log.debug("Questionnaire required: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.BAD_REQUEST, ValidationException.TYPE, "Questionnaire required", ex.getMessage(), request);
    }

    @ExceptionHandler(ListingNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleListingNotFound(
            ListingNotFoundException ex, HttpServletRequest request) {
        log.debug("Listing not found: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.NOT_FOUND, NotFoundException.TYPE, "Listing not found", ex.getMessage(), request);
    }

    @ExceptionHandler(ListingUnavailableException.class)
    public ResponseEntity<ProblemDetails> handleListingUnavailable(
            ListingUnavailableException ex, HttpServletRequest request) {
        log.warn("Listing service unavailable: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.SERVICE_UNAVAILABLE, UPSTREAM_TYPE, "Listing service unavailable", ex.getMessage(), request);
    }
}
