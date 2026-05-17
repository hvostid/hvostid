package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.hvostid.listing.entity.FlagReason;

@Schema(description = "Payload to report a listing as problematic")
public record FlagListingRequest(
        @NotNull(message = "Reason is required")
        @Schema(description = "Reason for the report", example = "SCAM", requiredMode = Schema.RequiredMode.REQUIRED)
        FlagReason reason,

        @Size(max = 1000, message = "Description too long")
        @Schema(
                description = "Free-form details (up to 1000 characters)",
                example = "Suspicious pricing and stolen photos")
        String description) {}
