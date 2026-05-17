package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import ru.hvostid.listing.entity.FlagReason;
import ru.hvostid.listing.entity.FlagStatus;
import ru.hvostid.listing.entity.ListingFlag;

@Schema(description = "Result of a listing report")
public record FlagListingResponse(
        @Schema(description = "Flag identifier", example = "1")
        Long id,

        @Schema(description = "Listing identifier", example = "42")
        Long listingId,

        @Schema(description = "Reporter user identifier", example = "5")
        Long reporterId,

        @Schema(description = "Reason for the report", example = "SCAM")
        FlagReason reason,

        @Schema(description = "Optional details supplied by the reporter")
        String description,

        @Schema(description = "Current review status", example = "PENDING")
        FlagStatus status,

        @Schema(description = "Submission timestamp", example = "2026-05-18T10:15:30Z")
        Instant createdAt) {
    public static FlagListingResponse from(ListingFlag flag) {
        return new FlagListingResponse(
                flag.getId(),
                flag.getListingId(),
                flag.getReporterId(),
                flag.getReason(),
                flag.getDescription(),
                flag.getStatus(),
                flag.getCreatedAt());
    }
}
