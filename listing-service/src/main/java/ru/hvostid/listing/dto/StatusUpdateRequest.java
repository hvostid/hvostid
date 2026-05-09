package ru.hvostid.listing.dto;

import jakarta.validation.constraints.NotNull;
import ru.hvostid.listing.entity.ListingStatus;

public record StatusUpdateRequest(
        @NotNull(message = "Status is required")
        ListingStatus status,

        String comment
) {}
