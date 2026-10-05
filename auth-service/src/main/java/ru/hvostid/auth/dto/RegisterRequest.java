package ru.hvostid.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import ru.hvostid.auth.validation.PasswordBytes;

/**
 * Request body for user registration.
 */
@Schema(description = "Fields required to create a new user account. The account is created with the BUYER role.")
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        @Schema(description = "User email; must be unique across the platform", example = "alice@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @PasswordBytes
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Schema(
                description = "Plain-text password: 8 or more characters, at most 72 UTF-8 bytes",
                example = "S3cret!pa$$")
        String password,

        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        @Schema(description = "Display name shown to other users", example = "Alice Ivanova")
        String name) {
    public RegisterRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        name = name == null ? null : name.strip();
    }
}
