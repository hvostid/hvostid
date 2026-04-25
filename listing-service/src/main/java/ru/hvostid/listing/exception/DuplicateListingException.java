package ru.hvostid.listing.exception;

public class DuplicateListingException extends RuntimeException {
    public DuplicateListingException(String message) {
        super(message);
    }
}
