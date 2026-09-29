package ru.hvostid.listing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.hvostid.listing.ListingConstants;

public record ListingDraftRequest(
        @NotBlank @Size(min = 3, max = 255) String title,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 255) String species,
        @Size(max = 255) String breed,

        @PositiveOrZero @Max(ListingConstants.MAX_AGE_MONTHS)
        Integer age,

        @PositiveOrZero Integer price,
        @NotBlank @Size(max = 255) String city) {}
