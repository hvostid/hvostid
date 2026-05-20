package ru.hvostid.listing.exception;

public class ListingDeletionConflictException extends RuntimeException {
    public ListingDeletionConflictException(String message) {
        super(message);
    }
}
