package ru.hvostid.listing.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ListingUpdateRequest(
        @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
        String title,

        @Size(max = 2000, message = "Description too long")
        String description,

        String species,

        String breed,

        @PositiveOrZero(message = "Age must be positive")
        Integer age,

        @PositiveOrZero(message = "Price must be positive")
        Integer price,

        String city
) {
}