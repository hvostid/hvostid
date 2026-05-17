package ru.hvostid.passport.exception;

public class UnsupportedPassportDocumentException extends RuntimeException {
    public UnsupportedPassportDocumentException(String message) {
        super(message);
    }
}
