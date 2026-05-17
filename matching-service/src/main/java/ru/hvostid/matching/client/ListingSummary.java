package ru.hvostid.matching.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Catalog summary of a single PUBLISHED listing, as returned by listing-service's
 * paginated GET /api/v1/listings endpoint. Used by recommendations to surface
 * listing details alongside each score.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListingSummary(
        Long id,
        Long sellerId,
        String title,
        String description,
        String species,
        String breed,
        Integer age,
        Integer price,
        String city,
        String passportId,
        Instant createdAt) {
    public ListingSnapshot toSnapshot() {
        return new ListingSnapshot(id, species, breed, age, passportId);
    }
}
