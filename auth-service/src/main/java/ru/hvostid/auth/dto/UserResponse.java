package ru.hvostid.auth.dto;

/**
 * Response body containing public user profile data.
 */
public record UserResponse(
        Long id,
        String email,
        String name,
        String role
) {
}
