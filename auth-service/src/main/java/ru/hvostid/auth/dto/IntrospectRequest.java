package ru.hvostid.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for token introspection.
 */
public record IntrospectRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}
