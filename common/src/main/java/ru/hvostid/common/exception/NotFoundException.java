package ru.hvostid.common.exception;

import org.springframework.http.HttpStatus;

/** Resource the caller asked for does not exist. Maps to 404. */
public class NotFoundException extends BusinessException {
    public static final String TYPE = "https://hvostid.example/errors/not-found";

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, TYPE, "Resource not found", detail);
    }

    public NotFoundException(String title, String detail) {
        super(HttpStatus.NOT_FOUND, TYPE, title, detail);
    }
}
