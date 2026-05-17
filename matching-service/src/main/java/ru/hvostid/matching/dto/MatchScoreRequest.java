package ru.hvostid.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request to calculate compatibility score for a listing")
public record MatchScoreRequest(
        @NotNull(message = "listingId is required")
        @Positive(message = "listingId must be positive")
        @Schema(description = "Listing identifier", example = "42")
        Long listingId) {}
