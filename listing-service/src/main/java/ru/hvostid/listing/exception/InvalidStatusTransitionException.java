package ru.hvostid.listing.exception;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
