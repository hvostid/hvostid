package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.hvostid.listing.entity.FlagStatus;

@Schema(description = "Moderator decision on a pending flag")
public record FlagReviewRequest(
        @NotNull(message = "Decision is required")
        @Schema(
                description = "Decision (only REVIEWED and DISMISSED are accepted)",
                example = "DISMISSED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        FlagStatus decision) {}
