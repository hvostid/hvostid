package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to create a new animal listing in DRAFT status")
public record ListingRequest(
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
        @Schema(description = "Listing title shown in catalog", example = "Friendly tabby kitten")
        String title,

        @Size(max = 2000, message = "Description too long")
        @Schema(
                description = "Free-form description (up to 2000 characters)",
                example = "Litter-trained, vaccinated, comes with passport")
        String description,

        @NotBlank(message = "Species is required") @Schema(description = "Animal species", example = "CAT")
        String species,

        @Schema(description = "Breed (optional, free-form)", example = "Domestic shorthair")
        String breed,

        @PositiveOrZero(message = "Age must be positive") @Schema(description = "Age in months", example = "4")
        Integer age,

        @PositiveOrZero(message = "Price must be positive")
        @Schema(description = "Price in minor currency units (rubles)", example = "3000")
        Integer price,

        @NotBlank(message = "City is required")
        @Schema(description = "City where the animal is currently located", example = "Saint Petersburg")
        String city,

        @NotBlank(message = "PassportId is required")
        @Schema(description = "Pet passport identifier from passport-service", example = "12")
        String passportId) {}
