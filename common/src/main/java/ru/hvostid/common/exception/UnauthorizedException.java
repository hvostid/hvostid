package ru.hvostid.common.exception;

import org.springframework.http.HttpStatus;

/** Caller is not authenticated. Maps to 401. */
public class UnauthorizedException extends BusinessException {
    public static final String PROBLEM_TYPE = "urn:problem-type:unauthorized";

    public UnauthorizedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, PROBLEM_TYPE, "Authentication required", detail);
    }

    public UnauthorizedException(String title, String detail) {
        super(HttpStatus.UNAUTHORIZED, PROBLEM_TYPE, title, detail);
    }
}
