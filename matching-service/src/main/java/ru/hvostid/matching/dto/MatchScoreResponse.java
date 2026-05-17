package ru.hvostid.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import ru.hvostid.matching.domain.CompatibilityLevel;

@Schema(description = "Compatibility score between buyer and pet listing")
public record MatchScoreResponse(
        @Schema(description = "Total compatibility score (0-100)", example = "78")
        int score,

        @Schema(description = "Compatibility level", example = "GOOD")
        CompatibilityLevel level,

        @Schema(description = "Per-factor breakdown") List<FactorScoreDto> factors,

        @Schema(
                description = "Overall human-readable summary",
                example =
                        "Good match overall. Your apartment is adequate for a medium-sized dog, but as a beginner you may need extra guidance with a husky.")
        String summary,

        @Schema(description = "Actionable tips to improve compatibility")
        List<String> tips,

        @Schema(description = "14-day adaptation plan in three phases")
        List<AdaptationPhaseDto> adaptationPlan,

        @Schema(description = "True when score used partial data (e.g. passport unavailable)", example = "false")
        boolean degraded,

        @Schema(
                description =
                        "Machine-readable reason when degraded=true (PASSPORT_ID_UNPARSEABLE, PASSPORT_UNAVAILABLE, SPECIES_UNKNOWN)",
                example = "PASSPORT_UNAVAILABLE",
                nullable = true)
        String degradedReason) {}
