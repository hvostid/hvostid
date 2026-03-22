package ru.hvostid.auth.exception;

/**
 * Thrown when a registration attempt uses an email that is already taken.
 */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email);
    }
}
