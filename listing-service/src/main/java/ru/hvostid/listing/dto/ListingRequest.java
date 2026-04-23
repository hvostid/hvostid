package ru.hvostid.listing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ListingRequest(
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
        String title,

        @Size(max = 2000, message = "Description too long")
        String description,

        @NotBlank(message = "Species is required")
        String species,

        String breed,

        @PositiveOrZero(message = "Age must be positive")
        Integer age,

        @PositiveOrZero(message = "Price must be positive")
        Integer price,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "PassportId is required")
        String passportId
) {
}