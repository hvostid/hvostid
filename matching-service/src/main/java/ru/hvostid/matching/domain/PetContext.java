package ru.hvostid.matching.domain;

import java.util.Optional;
import ru.hvostid.matching.client.ListingSnapshot;
import ru.hvostid.matching.client.PassportSnapshot;

public record PetContext(
        String species,
        String breed,
        Integer ageMonths,
        String temperament,
        String specialNeeds,
        BreedProfile profile,
        boolean passportAvailable) {

    public static PetContext from(ListingSnapshot listing, Optional<PassportSnapshot> passport) {
        String species = firstNonBlank(passport.map(PassportSnapshot::species).orElse(null), listing.species());
        String breed = firstNonBlank(passport.map(PassportSnapshot::breed).orElse(null), listing.breed());
        String temperament = passport.map(PassportSnapshot::temperament).orElse(null);
        String specialNeeds = passport.map(PassportSnapshot::specialNeeds).orElse(null);
        BreedProfile profile = BreedProfileCatalog.resolve(species, breed);
        return new PetContext(species, breed, listing.age(), temperament, specialNeeds, profile, passport.isPresent());
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }
}
