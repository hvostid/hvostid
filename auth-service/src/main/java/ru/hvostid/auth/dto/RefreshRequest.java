package ru.hvostid.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for token refresh.
 */
public record RefreshRequest(
        @NotBlank(message = "Refresh Token is required") String refreshToken) {}
