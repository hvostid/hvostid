package ru.hvostid.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating user profile.
 * All fields are optional; only non-null values will be applied.
 */
@Schema(description = "Editable profile fields")
public record UpdateProfileRequest(
        @Size(min = 1, max = 255, message = "name must be between 1 and 255 characters")
        String name,

        @Pattern(
                regexp = "^$|^\\+?[0-9 ()\\-]{5,30}$",
                message = "phone must contain only digits, spaces, parentheses, dashes, optional leading +")
        String phone,

        @Size(max = 255, message = "city must be at most 255 characters")
        String city,

        @Size(max = 2000, message = "bio must be at most 2000 characters")
        String bio,

        Boolean contactSharingEnabled) {
    public UpdateProfileRequest(String name, String phone, String city, String bio) {
        this(name, phone, city, bio, null);
    }

    public UpdateProfileRequest {
        name = name == null ? null : name.strip();
    }
}
