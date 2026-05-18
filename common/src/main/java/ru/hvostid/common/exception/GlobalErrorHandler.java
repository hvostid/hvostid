package ru.hvostid.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.hvostid.common.dto.ProblemDetails;

/**
 * Shared RFC 7807 problem-details responder. Sits at {@link Ordered#LOWEST_PRECEDENCE} so a
 * service can still register its own {@code @RestControllerAdvice} for service-specific
 * exception classes; whatever falls through lands here. Every response is content-typed
 * as {@code application/problem+json}, no stack traces ever leave the service, and the
 * {@code traceId} field is sourced from the {@code requestId} MDC slot populated by
 * {@link ru.hvostid.common.web.RequestIdMdcFilter}.
 */
@RestControllerAdvice
@Order
public class GlobalErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetails> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.debug("BusinessException {} at {}: {}", ex.status(), request.getRequestURI(), ex.getMessage());
        return problem(ex.status(), ex.type(), ex.title(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ProblemDetails.FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ProblemDetails.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.debug("Validation failed at {}: {}", request.getRequestURI(), errors);
        return problem(
                HttpStatus.BAD_REQUEST,
                ValidationException.TYPE,
                "Validation failed",
                "Request body failed validation",
                errors,
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetails> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ProblemDetails.FieldViolation> errors = new ArrayList<>();
        ex.getConstraintViolations().forEach(v -> {
            String field = v.getPropertyPath().toString();
            if (field.contains(".")) {
                field = field.substring(field.lastIndexOf('.') + 1);
            }
            errors.add(new ProblemDetails.FieldViolation(field, v.getMessage()));
        });
        log.debug("Constraint violations at {}: {}", request.getRequestURI(), errors);
        return problem(
                HttpStatus.BAD_REQUEST,
                ValidationException.TYPE,
                "Validation failed",
                "Request parameters failed validation",
                errors,
                request);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ProblemDetails> handleAccessDenied(Exception ex, HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(
                HttpStatus.FORBIDDEN,
                ForbiddenException.TYPE,
                "Access denied",
                "You do not have permission to perform this operation",
                null,
                request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetails> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials at {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(
                HttpStatus.UNAUTHORIZED,
                UnauthorizedException.TYPE,
                "Authentication required",
                ex.getMessage(),
                null,
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetails> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Malformed body at {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(
                HttpStatus.BAD_REQUEST,
                ValidationException.TYPE,
                "Malformed request body",
                "Request body could not be parsed",
                null,
                request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetails> handleIllegalArgument(Exception ex, HttpServletRequest request) {
        log.debug("Illegal argument at {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, ValidationException.TYPE, "Bad request", ex.getMessage(), null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGeneric(Exception ex, HttpServletRequest request) {
        // Stack trace stays on the server only.
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemDetailsFactory.INTERNAL_TYPE,
                "Internal server error",
                "An unexpected error occurred",
                null,
                request);
    }

    private static ResponseEntity<ProblemDetails> problem(
            HttpStatus status,
            String type,
            String title,
            String detail,
            List<ProblemDetails.FieldViolation> errors,
            HttpServletRequest request) {
        return ProblemDetailsFactory.problem(status, type, title, detail, errors, request);
    }
}
