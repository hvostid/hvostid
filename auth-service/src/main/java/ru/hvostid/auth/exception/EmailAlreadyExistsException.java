package ru.hvostid.auth.exception;

/**
 * Thrown when a registration attempt uses an email that is already taken.
 */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        // Detail intentionally does not echo the email back to avoid an
        // enumeration channel; the original value still appears in MDC
        // (userId/email-on-attempt) for server-side audit.
        super("This email is already registered");
    }
}
