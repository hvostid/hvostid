package ru.hvostid.listing.repository;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
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
            predicates.add(cb.equal(root.get("status"), ListingStatus.PUBLISHED));
            if (filters != null && !filters.isEmpty()) {
                addTextPredicates(predicates, root, cb, filters);
                addNumericPredicates(predicates, root, cb, filters);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addTextPredicates(
            List<Predicate> predicates, Root<Listing> root, CriteriaBuilder cb, ListingFilterRequest filters) {
        addLikeIfPresent(predicates, root.get("species"), cb, filters.species());
        addLikeIfPresent(predicates, root.get("breed"), cb, filters.breed());
        if (filters.city() != null && !filters.city().isBlank()) {
            predicates.add(cb.equal(cb.lower(root.get("city")), filters.city().toLowerCase()));
        }
    }

    private static void addLikeIfPresent(
            List<Predicate> predicates, Path<String> column, CriteriaBuilder cb, @Nullable String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        predicates.add(cb.like(cb.lower(column), "%" + value.toLowerCase() + "%"));
    }

    private static void addNumericPredicates(
            List<Predicate> predicates, Root<Listing> root, CriteriaBuilder cb, ListingFilterRequest filters) {
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
    }
}
