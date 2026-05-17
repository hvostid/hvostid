package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to return a listing from MODERATION back to DRAFT")
public record ModerationRejectRequest(
        @NotBlank(message = "Comment is required")
        @Size(max = 500, message = "Comment too long, max 500 characters")
        @Schema(
                description = "Reason the listing is sent back to the seller",
                example = "Photos are blurry, please re-upload",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String comment) {}
