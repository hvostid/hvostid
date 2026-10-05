package ru.hvostid.auth.dto;

import jakarta.validation.constraints.*;
import ru.hvostid.auth.validation.PasswordBytes;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 8, max = 72) @PasswordBytes String password) {}
