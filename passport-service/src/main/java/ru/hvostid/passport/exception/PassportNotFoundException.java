package ru.hvostid.passport.exception;

public class PassportNotFoundException extends RuntimeException {
    public PassportNotFoundException(String message) {
        super(message);
    }
}
