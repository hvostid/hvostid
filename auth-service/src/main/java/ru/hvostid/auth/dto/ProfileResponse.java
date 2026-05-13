package ru.hvostid.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response body containing full user profile data including roles.
 */
@Schema(description = "Full user profile, including assigned roles and editable contact fields")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
        @Schema(description = "User identifier", example = "42")
        Long id,

        @Schema(description = "Registered email address", example = "alice@example.com")
        String email,

        @Schema(description = "Display name", example = "Alice Ivanova")
        String name,

        @Schema(description = "Assigned roles", example = "[\"BUYER\",\"SELLER\"]")
        List<String> roles,

        @Schema(description = "Contact phone (optional)", example = "+7 999 000-00-00")
        String phone,

        @Schema(description = "City of residence (optional)", example = "Saint Petersburg")
        String city,

        @Schema(description = "Free-form bio (optional)", example = "Cat lover, foster volunteer")
        String bio,

        @Schema(description = "Aggregated rating across listings (optional)", example = "4.7")
        Double rating) {}
