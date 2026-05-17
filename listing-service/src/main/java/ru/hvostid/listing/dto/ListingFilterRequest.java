package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Filters for listing search")
public record ListingFilterRequest(
        @Schema(description = "Animal species (case-insensitive partial match)", example = "dog")
        String species,

        @Schema(description = "Breed (case-insensitive partial match)", example = "labrador")
        String breed,

        @Min(0) @Max(5000) @Schema(description = "Minimum age in months", example = "1")
        Integer ageMin,

        @Min(0) @Max(5000) @Schema(description = "Maximum age in months", example = "60")
        Integer ageMax,

        @Min(0) @Max(999999999) @Schema(description = "Minimum price in rubles", example = "5000")
        Integer priceMin,

        @Min(0) @Max(999999999) @Schema(description = "Maximum price in rubles", example = "50000")
        Integer priceMax,

        @Schema(description = "City (case-insensitive exact match)", example = "Moscow")
        String city) {
    public boolean isEmpty() {
        return (species == null || species.isBlank())
                && (breed == null || breed.isBlank())
                && ageMin == null
                && ageMax == null
                && priceMin == null
                && priceMax == null
                && (city == null || city.isBlank());
    }

    public boolean hasAgeBounds() {
        return ageMin != null || ageMax != null;
    }

    public boolean hasPriceBounds() {
        return priceMin != null || priceMax != null;
    }
}
