package ru.hvostid.common.contract.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for token introspection.
 */
public record IntrospectRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}
