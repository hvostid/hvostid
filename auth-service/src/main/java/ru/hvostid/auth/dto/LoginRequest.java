package ru.hvostid.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import ru.hvostid.auth.validation.PasswordBytes;

/**
 * Request body for user login.
 */
@Schema(description = "Credentials submitted to obtain an access/refresh token pair")
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        @Schema(description = "Registered email address", example = "alice@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @PasswordBytes
        @Schema(description = "Plain-text password (transported over TLS only)", example = "S3cret!pa$$")
        String password) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
