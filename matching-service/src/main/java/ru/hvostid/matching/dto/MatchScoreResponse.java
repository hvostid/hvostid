package ru.hvostid.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import ru.hvostid.matching.domain.CompatibilityLevel;
import ru.hvostid.matching.domain.CompatibilityResult;

@Schema(description = "Compatibility score between buyer and pet listing")
public record MatchScoreResponse(
        @Schema(description = "Total compatibility score (0-100)", example = "78")
        int score,

        @Schema(description = "Compatibility level", example = "GOOD")
        CompatibilityLevel level,

        @Schema(description = "Per-factor breakdown") List<FactorScoreDto> factors,

        @Schema(description = "True when score used partial data (e.g. passport unavailable)", example = "false")
        boolean degraded) {

    public static MatchScoreResponse from(CompatibilityResult result, boolean degraded) {
        List<FactorScoreDto> factors =
                result.factors().stream().map(FactorScoreDto::from).toList();
        return new MatchScoreResponse(result.score(), result.level(), factors, degraded);
    }
}
