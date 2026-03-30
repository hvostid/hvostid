package ru.hvostid.auth.exception;

/**
 * Thrown when a user is not found by their identifier.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("User not found: " + userId);
    }
}
