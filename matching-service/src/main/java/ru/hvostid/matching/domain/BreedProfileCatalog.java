package ru.hvostid.matching.domain;

import java.util.Locale;
import java.util.Map;

public final class BreedProfileCatalog {
    static final BreedProfile NEUTRAL_PROFILE = new BreedProfile(PetSize.MEDIUM, 2, 2, false, false, 7000, 2);

    private static final BreedProfile DEFAULT_DOG = new BreedProfile(PetSize.MEDIUM, 2, 3, false, false, 8000, 2);
    private static final BreedProfile DEFAULT_CAT = new BreedProfile(PetSize.SMALL, 1, 2, false, false, 5000, 2);

    private static final Map<String, BreedProfile> BY_BREED = Map.ofEntries(
            Map.entry("husky", new BreedProfile(PetSize.LARGE, 3, 4, true, false, 12000, 3)),
            Map.entry("labrador", new BreedProfile(PetSize.LARGE, 2, 3, true, false, 9000, 2)),
            Map.entry("labrador retriever", new BreedProfile(PetSize.LARGE, 2, 3, true, false, 9000, 2)),
            Map.entry("german shepherd", new BreedProfile(PetSize.LARGE, 3, 4, true, false, 11000, 3)),
            Map.entry("golden retriever", new BreedProfile(PetSize.LARGE, 2, 3, true, false, 10000, 2)),
            Map.entry("beagle", new BreedProfile(PetSize.MEDIUM, 2, 3, false, false, 7000, 2)),
            Map.entry("poodle", new BreedProfile(PetSize.MEDIUM, 2, 3, false, true, 9000, 2)),
            Map.entry("chihuahua", new BreedProfile(PetSize.SMALL, 1, 2, false, false, 5000, 2)),
            Map.entry("mixed", new BreedProfile(PetSize.MEDIUM, 2, 3, false, false, 7000, 2)),
            Map.entry("siamese", new BreedProfile(PetSize.SMALL, 2, 3, false, false, 6000, 3)),
            Map.entry("domestic shorthair", new BreedProfile(PetSize.SMALL, 1, 2, false, false, 4500, 2)),
            Map.entry("maine coon", new BreedProfile(PetSize.LARGE, 2, 2, false, false, 7000, 2)),
            Map.entry("persian", new BreedProfile(PetSize.MEDIUM, 2, 1, false, false, 6500, 3)),
            Map.entry("british shorthair", new BreedProfile(PetSize.MEDIUM, 1, 2, false, false, 5500, 2)),
            Map.entry("sphynx", new BreedProfile(PetSize.SMALL, 2, 2, false, true, 8000, 3)));

    private BreedProfileCatalog() {}

    public static BreedResolution resolve(String species, String breed) {
        String normalizedSpecies = species == null ? "" : species.trim().toLowerCase(Locale.ROOT);
        if (normalizedSpecies.isBlank()) {
            return new BreedResolution(NEUTRAL_PROFILE, true);
        }

        if (breed != null && !breed.isBlank()) {
            BreedProfile byBreed = BY_BREED.get(normalizeKey(breed));
            if (byBreed != null) {
                return new BreedResolution(byBreed, false);
            }
        }

        if (normalizedSpecies.contains("cat") || normalizedSpecies.contains("кош")) {
            return new BreedResolution(DEFAULT_CAT, false);
        }
        if (normalizedSpecies.contains("dog") || normalizedSpecies.contains("собак")) {
            return new BreedResolution(DEFAULT_DOG, false);
        }
        return new BreedResolution(NEUTRAL_PROFILE, true);
    }

    private static String normalizeKey(String breed) {
        return breed.trim().toLowerCase(Locale.ROOT);
    }
}
