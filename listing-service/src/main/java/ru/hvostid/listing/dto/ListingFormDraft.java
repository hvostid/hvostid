package ru.hvostid.listing.dto;

import jakarta.validation.constraints.*;

public record ListingFormDraft(
        @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @Size(max = 255) String species,
        @Size(max = 255) String breed,

        @PositiveOrZero @Max(ru.hvostid.listing.ListingConstants.MAX_AGE_MONTHS)
        Integer age,

        @PositiveOrZero Integer price,
        @Size(max = 255) String city,
        @Size(max = 32) String passportId,
        @NotNull @PositiveOrZero Long version) {
    public ListingFormDraft nextVersion() {
        return new ListingFormDraft(title, description, species, breed, age, price, city, passportId, version + 1);
    }
}
