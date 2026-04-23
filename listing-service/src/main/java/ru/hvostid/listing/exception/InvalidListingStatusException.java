package ru.hvostid.listing.exception;

public class InvalidListingStatusException extends RuntimeException {
    public InvalidListingStatusException(String message) {
        super(message);
    }
}