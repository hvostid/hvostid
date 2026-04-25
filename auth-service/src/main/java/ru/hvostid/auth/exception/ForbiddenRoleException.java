package ru.hvostid.auth.exception;

/**
 * Thrown when a user attempts to assign themselves a restricted role
 * (e.g. MODERATOR or ADMIN).
 */
public class ForbiddenRoleException extends RuntimeException {
    public ForbiddenRoleException(String role) {
        super("Cannot self-assign role: " + role);
    }
}
