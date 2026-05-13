package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Editable listing fields. Omitted (null) fields remain unchanged.")
public record ListingUpdateRequest(
        @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
        @Schema(description = "Listing title", example = "Friendly tabby kitten - updated")
        String title,

        @Size(max = 2000, message = "Description too long") @Schema(description = "Listing description")
        String description,

        @Schema(description = "Animal species", example = "CAT")
        String species,

        @Schema(description = "Breed", example = "Domestic shorthair")
        String breed,

        @PositiveOrZero(message = "Age must be positive") @Schema(description = "Age in months", example = "5")
        Integer age,

        @PositiveOrZero(message = "Price must be positive") @Schema(description = "Price in rubles", example = "3500")
        Integer price,

        @Schema(description = "City where the animal is located", example = "Moscow")
        String city,

        @Schema(description = "Pet passport identifier", example = "12")
        String passportId) {}
