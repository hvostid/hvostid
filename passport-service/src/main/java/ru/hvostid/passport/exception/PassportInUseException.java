package ru.hvostid.passport.exception;

public class PassportInUseException extends RuntimeException {
    public PassportInUseException(String message) {
        super(message);
    }
}
