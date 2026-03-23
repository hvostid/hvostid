package ru.hvostid.auth.exception;

/**
 * Thrown when a refresh token is invalid or expired.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
