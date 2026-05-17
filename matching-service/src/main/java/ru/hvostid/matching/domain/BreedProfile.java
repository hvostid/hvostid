package ru.hvostid.matching.domain;

public record BreedProfile(
        PetSize size,
        int careDifficulty,
        int activityNeeds,
        boolean needsYard,
        boolean hypoallergenic,
        int estimatedMonthlyCost,
        int attentionNeeds) {

    public BreedProfile {
        if (careDifficulty < 1 || careDifficulty > 3) {
            throw new IllegalArgumentException("careDifficulty must be 1..3");
        }
        if (activityNeeds < 1 || activityNeeds > 4) {
            throw new IllegalArgumentException("activityNeeds must be 1..4");
        }
        if (attentionNeeds < 1 || attentionNeeds > 3) {
            throw new IllegalArgumentException("attentionNeeds must be 1..3");
        }
    }
}
