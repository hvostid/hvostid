package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether the given passport is referenced by at least one PUBLISHED listing")
public record PassportPublishedStatusResponse(
        @Schema(description = "Passport identifier", example = "42")
        String passportId,

        @Schema(description = "True if at least one PUBLISHED listing references this passport", example = "true")
        boolean hasPublishedListing) {}
