package ru.hvostid.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response body containing public user profile data.
 * Returned on registration and used by auth endpoints.
 */
@Schema(description = "Compact user view returned by auth-related endpoints")
public record UserResponse(
        @Schema(description = "User identifier", example = "42")
        Long id,

        @Schema(description = "Registered email address", example = "alice@example.com")
        String email,

        @Schema(description = "Display name", example = "Alice Ivanova")
        String name,

        @Schema(description = "Assigned roles", example = "[\"BUYER\"]")
        List<String> roles) {}
