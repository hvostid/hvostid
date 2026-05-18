package ru.hvostid.listing.repository;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import ru.hvostid.listing.dto.ListingFilterRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;

public final class ListingSpecifications {

    private ListingSpecifications() {}

    /**
     * Builds a dynamic query for published listings with optional filters.
     * All text filters are case-insensitive.
     */
    public static Specification<Listing> withFilters(@Nullable ListingFilterRequest filters) {
        return (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only published listings are visible to public search
            predicates.add(cb.equal(root.get("status"), ListingStatus.PUBLISHED));

            if (filters == null || filters.isEmpty()) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (filters.species() != null && !filters.species().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("species")), "%" + filters.species().toLowerCase() + "%"));
            }

            if (filters.breed() != null && !filters.breed().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("breed")), "%" + filters.breed().toLowerCase() + "%"));
            }

            if (filters.city() != null && !filters.city().isBlank()) {
                predicates.add(
                        cb.equal(cb.lower(root.get("city")), filters.city().toLowerCase()));
            }

            // Numeric range filters
            if (filters.ageMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("age"), filters.ageMin()));
            }
            if (filters.ageMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("age"), filters.ageMax()));
            }

            if (filters.priceMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filters.priceMin()));
            }
            if (filters.priceMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filters.priceMax()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
