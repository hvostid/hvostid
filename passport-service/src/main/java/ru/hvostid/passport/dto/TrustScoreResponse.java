package ru.hvostid.passport.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trust score (0-100) for a pet passport with per-component breakdown")
public record TrustScoreResponse(
        @Schema(description = "Aggregated trust score, 0-100", example = "75")
        int score,

        @Schema(description = "Component contributions") TrustScoreBreakdown breakdown) {}
