package ru.hvostid.auth.dto;

import jakarta.validation.constraints.*;

public record AccountTokenRequest(@NotBlank @Size(max = 128) String token) {}
