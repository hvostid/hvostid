package ru.hvostid.auth.dto;

import java.util.List;

/**
 * Response body containing public user profile data.
 * Returned on registration and used by auth endpoints.
 */
public record UserResponse(
        Long id,
        String email,
        String name,
        List<String> roles
) {
}
