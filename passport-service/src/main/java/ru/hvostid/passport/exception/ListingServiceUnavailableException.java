package ru.hvostid.passport.exception;

public class ListingServiceUnavailableException extends RuntimeException {
    public ListingServiceUnavailableException(String message) {
        super(message);
    }

    public ListingServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
