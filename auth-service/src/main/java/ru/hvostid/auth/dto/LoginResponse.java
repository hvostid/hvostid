package ru.hvostid.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body returned after successful login.
 */
@Schema(description = "Opaque access and refresh tokens issued after a successful login or refresh")
public record LoginResponse(
        @Schema(
                description = "Opaque access token; send as 'Authorization: Bearer <token>' to protected endpoints",
                example = "atk_8c6f8d2c4f3b4d0d9c1e8e92e9c0d1a4")
        String accessToken,

        @Schema(
                description = "Opaque refresh token; exchange via POST /auth/refresh before the access token expires",
                example = "rtk_2a4c6b8e1f0d4d35a9b7c81e92d3f445")
        String refreshToken,

        @Schema(description = "Access-token lifetime in seconds", example = "1800")
        long expiresIn) {}
