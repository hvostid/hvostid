package ru.hvostid.listing.exception;

public class FlagNotFoundException extends RuntimeException {
    public FlagNotFoundException(String message) {
        super(message);
    }
}
