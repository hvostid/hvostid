package ru.hvostid.passport.exception;

public class PassportDocumentNotFoundException extends RuntimeException {
    public PassportDocumentNotFoundException(String message) {
        super(message);
    }
}
