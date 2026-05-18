package ru.hvostid.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import ru.hvostid.common.dto.ProblemDetails;
import ru.hvostid.common.web.RequestIdMdcFilter;

/**
 * Builds an RFC 7807 {@link ResponseEntity} consistently across services. Used by the
 * shared {@code GlobalErrorHandler} and by service-specific {@code @RestControllerAdvice}s
 * so that every error body shares the same fields, content-type, and traceId source.
 */
public final class ProblemDetailsFactory {
    public static final String INTERNAL_TYPE = "urn:problem-type:internal";

    private ProblemDetailsFactory() {}

    public static ResponseEntity<ProblemDetails> problem(
            HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
        return problem(status, type, title, detail, null, request);
    }

    public static ResponseEntity<ProblemDetails> problem(
            HttpStatus status,
            String type,
            String title,
            String detail,
            List<ProblemDetails.FieldViolation> errors,
            HttpServletRequest request) {
        ProblemDetails body = new ProblemDetails(
                type,
                title,
                status.value(),
                detail,
                request == null ? null : request.getRequestURI(),
                MDC.get(RequestIdMdcFilter.MDC_KEY),
                errors,
                Instant.now());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
