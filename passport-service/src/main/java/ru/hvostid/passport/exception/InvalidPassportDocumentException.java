package ru.hvostid.passport.exception;

public class InvalidPassportDocumentException extends RuntimeException {
    public InvalidPassportDocumentException(String message) {
        super(message);
    }

    public InvalidPassportDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
