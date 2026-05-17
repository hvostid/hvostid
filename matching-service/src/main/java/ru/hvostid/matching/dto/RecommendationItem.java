package ru.hvostid.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hvostid.matching.client.ListingSummary;
import ru.hvostid.matching.domain.CompatibilityLevel;

@Schema(description = "A single recommended listing with its compatibility score")
public record RecommendationItem(
        @Schema(description = "Catalog listing") ListingSummary listing,

        @Schema(description = "Compatibility score (0-100)", example = "92")
        int score,

        @Schema(description = "Compatibility level", example = "GREAT")
        CompatibilityLevel level) {}
