package ru.hvostid.common.exception;

import org.springframework.http.HttpStatus;

/** Resource the caller asked for does not exist. Maps to 404. */
public class NotFoundException extends BusinessException {
    public static final String PROBLEM_TYPE = "urn:problem-type:not-found";

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, PROBLEM_TYPE, "Resource not found", detail);
    }

    public NotFoundException(String title, String detail) {
        super(HttpStatus.NOT_FOUND, PROBLEM_TYPE, title, detail);
    }
}
