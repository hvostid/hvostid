package ru.hvostid.auth.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record PasswordResetRequest(
        @NotBlank @Email @Size(max = 255) String email) {
    public PasswordResetRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
