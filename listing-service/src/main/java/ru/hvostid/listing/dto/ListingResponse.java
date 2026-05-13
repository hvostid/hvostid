package ru.hvostid.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;

@Schema(description = "Animal listing as exposed by the catalog API")
public record ListingResponse(
        @Schema(description = "Listing identifier", example = "1")
        Long id,

        @Schema(description = "Seller user identifier", example = "42")
        Long sellerId,

        @Schema(description = "Listing title", example = "Friendly tabby kitten")
        String title,

        @Schema(description = "Listing description") String description,

        @Schema(description = "Animal species", example = "CAT")
        String species,

        @Schema(description = "Breed", example = "Domestic shorthair")
        String breed,

        @Schema(description = "Age in months", example = "4")
        Integer age,

        @Schema(description = "Price in rubles", example = "3000")
        Integer price,

        @Schema(description = "City where the animal is located", example = "Saint Petersburg")
        String city,

        @Schema(description = "Listing status") ListingStatus status,

        @Schema(description = "Pet passport identifier", example = "12")
        String passportId,

        @Schema(description = "Creation timestamp", example = "2026-05-13T10:15:30Z")
        Instant createdAt,

        @Schema(description = "Last update timestamp", example = "2026-05-13T11:00:00Z")
        Instant updatedAt) {
    public static ListingResponse from(Listing listing) {
        return new ListingResponse(
                listing.getId(),
                listing.getSellerId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getSpecies(),
                listing.getBreed(),
                listing.getAge(),
                listing.getPrice(),
                listing.getCity(),
                listing.getStatus(),
                listing.getPassportId(),
                listing.getCreatedAt(),
                listing.getUpdatedAt());
    }
}
