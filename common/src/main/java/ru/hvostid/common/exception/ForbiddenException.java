package ru.hvostid.common.exception;

import org.springframework.http.HttpStatus;

/** Caller is authenticated but not allowed to perform the operation. Maps to 403. */
public class ForbiddenException extends BusinessException {
    public static final String PROBLEM_TYPE = "urn:problem-type:forbidden";

    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, PROBLEM_TYPE, "Access denied", detail);
    }

    public ForbiddenException(String title, String detail) {
        super(HttpStatus.FORBIDDEN, PROBLEM_TYPE, title, detail);
    }
}
