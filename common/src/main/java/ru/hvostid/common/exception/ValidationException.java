package ru.hvostid.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Request body or query failed domain validation that goes beyond annotation-driven
 * Bean Validation (which is already handled separately and produces 400 with field errors).
 * Maps to 400.
 */
public class ValidationException extends BusinessException {
    public static final String TYPE = "urn:problem-type:validation";

    public ValidationException(String detail) {
        super(HttpStatus.BAD_REQUEST, TYPE, "Validation failed", detail);
    }

    public ValidationException(String title, String detail) {
        super(HttpStatus.BAD_REQUEST, TYPE, title, detail);
    }
}
