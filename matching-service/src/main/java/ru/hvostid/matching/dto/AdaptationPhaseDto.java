package ru.hvostid.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One phase of the 14-day adaptation plan")
public record AdaptationPhaseDto(
        @Schema(description = "Inclusive day range", example = "1-3")
        String dayRange,

        @Schema(description = "Phase title", example = "Getting to know each other")
        String title,

        @Schema(description = "Tasks for this phase") List<String> tasks) {}
