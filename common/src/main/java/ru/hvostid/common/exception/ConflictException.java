package ru.hvostid.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Caller's request conflicts with current resource state (duplicate, optimistic-lock
 * mismatch, etc.). Maps to 409.
 */
public class ConflictException extends BusinessException {
    public static final String PROBLEM_TYPE = "urn:problem-type:conflict";

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, PROBLEM_TYPE, "Conflict", detail);
    }

    public ConflictException(String title, String detail) {
        super(HttpStatus.CONFLICT, PROBLEM_TYPE, title, detail);
    }
}
