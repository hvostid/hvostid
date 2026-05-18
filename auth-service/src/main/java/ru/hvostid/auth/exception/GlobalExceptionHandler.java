package ru.hvostid.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Auth-service specific exception mappings. Bean Validation and the generic fallback are
 * handled by {@code common.exception.GlobalErrorHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetails> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Email conflict: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.CONFLICT, ConflictException.TYPE, "Email already registered", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetails> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.UNAUTHORIZED, UnauthorizedException.TYPE, "Invalid credentials", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetails> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex, HttpServletRequest request) {
        log.warn("Refresh token rejected: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.UNAUTHORIZED, UnauthorizedException.TYPE, "Invalid refresh token", ex.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        log.warn("User not found: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.NOT_FOUND, NotFoundException.TYPE, "User not found", ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenRoleException.class)
    public ResponseEntity<ProblemDetails> handleForbiddenRole(ForbiddenRoleException ex, HttpServletRequest request) {
        log.warn("Forbidden role assignment: {}", ex.getMessage());
        return ProblemDetailsFactory.problem(
                HttpStatus.FORBIDDEN, ForbiddenException.TYPE, "Forbidden role assignment", ex.getMessage(), request);
    }
}
