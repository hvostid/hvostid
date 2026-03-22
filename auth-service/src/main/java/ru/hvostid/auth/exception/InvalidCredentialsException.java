package ru.hvostid.auth.exception;

/**
 * Thrown when login credentials are invalid (wrong email or password).
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
