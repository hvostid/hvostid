package ru.hvostid.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for token refresh.
 */
@Schema(description = "Refresh-token exchange request body")
public record RefreshRequest(
        @NotBlank(message = "Refresh Token is required")
        @Schema(
                description = "Opaque refresh token previously issued by /auth/login or /auth/refresh",
                example = "rtk_2a4c6b8e1f0d4d35a9b7c81e92d3f445")
        String refreshToken) {}
