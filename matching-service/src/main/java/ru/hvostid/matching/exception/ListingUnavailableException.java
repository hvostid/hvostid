package ru.hvostid.matching.exception;

public class ListingUnavailableException extends RuntimeException {
    public ListingUnavailableException(String message) {
        super(message);
    }

    public ListingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
