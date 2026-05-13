package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.hvostid.listing.entity.ListingStatus;

@Schema(description = "Target status for a listing-state transition")
public record StatusUpdateRequest(
        @NotNull(message = "Status is required") @Schema(description = "New listing status", example = "MODERATION")
        ListingStatus status,

        @Schema(
                description = "Optional reviewer comment (recorded on REJECTED transitions)",
                example = "Photos are too dark; please re-upload")
        String comment) {}
