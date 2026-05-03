package ru.hvostid.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for adding a role to the current user.
 */
@Schema(description = "Role to add to the user")
public record AddRoleRequest(
        @NotBlank(message = "role must not be blank")
        @Schema(description = "Role name (only SELLER is allowed for self-assignment)", example = "SELLER")
        String role) {}
