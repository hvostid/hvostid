package ru.hvostid.common.security;

import java.util.Arrays;

/**
 * User roles shared across services.
 */
public enum UserRole {
    BUYER("BUYER"),
    SELLER("SELLER"),
    MODERATOR("MODERATOR"),
    ADMIN("ADMIN");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public static UserRole fromValue(String value) {
        return Arrays.stream(values())
                .filter(role -> role.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user role: " + value));
    }

    public String value() {
        return value;
    }

    public String authority() {
        return "ROLE_" + value;
    }
}
