package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Listing detail view used by the moderation panel, with all flags attached")
public record ModeratedListingDetailResponse(
        @Schema(description = "Listing data") ListingResponse listing,

        @Schema(description = "All flags reported against this listing, newest first")
        List<FlagListingResponse> flags) {}
