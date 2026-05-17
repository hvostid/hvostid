package ru.hvostid.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hvostid.matching.domain.FactorScore;

@Schema(description = "Score breakdown for a single compatibility factor")
public record FactorScoreDto(
        @Schema(description = "Factor identifier", example = "living_space")
        String name,

        @Schema(description = "Points earned for this factor", example = "18")
        int score,

        @Schema(description = "Maximum points for this factor", example = "20")
        int maxScore,

        @Schema(description = "Human-readable explanation") String comment) {

    public static FactorScoreDto from(FactorScore factor) {
        return new FactorScoreDto(factor.name(), factor.score(), factor.maxScore(), factor.comment());
    }
}
