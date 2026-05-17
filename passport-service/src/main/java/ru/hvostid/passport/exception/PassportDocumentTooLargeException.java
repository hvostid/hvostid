package ru.hvostid.passport.exception;

public class PassportDocumentTooLargeException extends RuntimeException {
    public PassportDocumentTooLargeException(String message) {
        super(message);
    }
}
