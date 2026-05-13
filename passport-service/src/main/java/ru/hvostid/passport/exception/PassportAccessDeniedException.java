package ru.hvostid.passport.exception;

public class PassportAccessDeniedException extends RuntimeException {
    public PassportAccessDeniedException(String message) {
        super(message);
    }
}
