package ru.hvostid.auth.dto;

/**
 * Response body returned after successful login.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
