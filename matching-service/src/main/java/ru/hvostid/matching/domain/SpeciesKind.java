package ru.hvostid.matching.domain;

import java.util.Locale;

/**
 * Coarse classification of the pet's species used both for breed-profile lookup
 * and for matching the buyer's allergy text against the pet. Keeping the keyword
 * lists in one place avoids the consistency drift gemini flagged in PR review:
 * {@code BreedProfileCatalog} and the allergy check were previously using
 * different keyword sets, so "feline" matched in one path but not the other.
 */
public enum SpeciesKind {
    CAT,
    DOG,
    OTHER;

    private static final String[] CAT_KEYWORDS = {"cat", "feline", "кош"};
    private static final String[] DOG_KEYWORDS = {"dog", "canine", "собак"};

    public static SpeciesKind classify(String species) {
        if (species == null) {
            return OTHER;
        }
        String normalized = species.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return OTHER;
        }
        if (containsAny(normalized, CAT_KEYWORDS)) {
            return CAT;
        }
        if (containsAny(normalized, DOG_KEYWORDS)) {
            return DOG;
        }
        return OTHER;
    }

    private static boolean containsAny(String text, String[] tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
