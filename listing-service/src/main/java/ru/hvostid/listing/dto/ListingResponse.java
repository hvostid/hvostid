package ru.hvostid.listing.dto;

import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;

import java.time.Instant;

public record ListingResponse(
        Long id,
        Long sellerId,
        String title,
        String description,
        String species,
        String breed,
        Integer age,
        Integer price,
        String city,
        ListingStatus status,
        String passportId,
        Instant createdAt,
        Instant updatedAt
) {
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
                listing.getUpdatedAt()
        );
    }
}