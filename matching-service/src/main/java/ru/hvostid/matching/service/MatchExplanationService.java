package ru.hvostid.matching.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.hvostid.matching.domain.CompatibilityFactor;
import ru.hvostid.matching.domain.CompatibilityLevel;
import ru.hvostid.matching.domain.CompatibilityResult;
import ru.hvostid.matching.domain.FactorScore;
import ru.hvostid.matching.domain.PetContext;
import ru.hvostid.matching.domain.SpeciesKind;
import ru.hvostid.matching.entity.BuyerQuestionnaire;

@Component
public class MatchExplanationService {

    private static final double WEAK_FACTOR_RATIO = 0.5;

    public String buildSummary(
            BuyerQuestionnaire questionnaire, PetContext pet, CompatibilityResult result, boolean degraded) {
        StringBuilder sb = new StringBuilder();
        sb.append(levelOpening(result.level()));

        List<FactorScore> weakFactors = weakFactors(result);
        if (!weakFactors.isEmpty()) {
            sb.append(' ');
            sb.append(weakFactorsSummary(weakFactors));
        }

        String breedNote = breedExperienceNote(pet, result);
        if (!breedNote.isEmpty()) {
            sb.append(' ').append(breedNote);
        }

        if (degraded) {
            sb.append(" Score is based on partial passport data; verify details with the seller.");
        }

        return sb.toString().trim();
    }

    public List<String> buildTips(BuyerQuestionnaire questionnaire, PetContext pet, CompatibilityResult result) {
        Set<String> tips = new LinkedHashSet<>();

        for (FactorScore factor : weakFactors(result)) {
            tipForFactor(factor).ifPresent(tips::add);
        }

        addSpeciesTips(tips, pet);
        addProfileTips(tips, pet, result);

        if (tips.isEmpty()) {
            tips.add("Maintain routines and monitor the pet's behavior during the first two weeks");
        }

        return List.copyOf(limitTips(tips, 6));
    }

    private static String levelOpening(CompatibilityLevel level) {
        return switch (level) {
            case GREAT -> "Excellent match overall.";
            case GOOD -> "Good match overall.";
            case RISKY -> "Match has notable risks that need attention.";
            case NOT_RECOMMENDED -> "This match is not recommended based on your profile.";
        };
    }

    private static List<FactorScore> weakFactors(CompatibilityResult result) {
        return result.factors().stream()
                .filter(f -> f.criticalConflict() || (double) f.score() / f.maxScore() < WEAK_FACTOR_RATIO)
                .toList();
    }

    private static String weakFactorsSummary(List<FactorScore> weakFactors) {
        List<String> parts = new ArrayList<>();
        for (FactorScore factor : weakFactors) {
            parts.add(factor.comment());
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(" Also, ", parts.subList(0, Math.min(2, parts.size())));
    }

    private static String breedExperienceNote(PetContext pet, CompatibilityResult result) {
        FactorScore experience = result.factors().stream()
                .filter(f -> f.factor() == CompatibilityFactor.EXPERIENCE)
                .findFirst()
                .orElse(null);
        FactorScore activity = result.factors().stream()
                .filter(f -> f.factor() == CompatibilityFactor.ACTIVITY)
                .findFirst()
                .orElse(null);

        String breed = pet.breed() == null || pet.breed().isBlank() ? "this pet" : pet.breed();
        if (experience != null && (double) experience.score() / experience.maxScore() < WEAK_FACTOR_RATIO) {
            return "Extra guidance may help with " + breed + ".";
        }
        if (activity != null && (double) activity.score() / activity.maxScore() < WEAK_FACTOR_RATIO) {
            return "Activity needs for " + breed + " may not align with your current lifestyle.";
        }
        return "";
    }

    private static java.util.Optional<String> tipForFactor(FactorScore factor) {
        return switch (factor.factor()) {
            case LIVING_SPACE -> java.util.Optional.of("Consider whether you can provide more space or outdoor access");
            case CHILDREN -> java.util.Optional.of("Supervise all interactions with children and introduce slowly");
            case ALLERGIES ->
                factor.criticalConflict()
                        ? java.util.Optional.of("Consult an allergist before proceeding with adoption")
                        : java.util.Optional.of("Discuss allergy management strategies with your doctor");
            case EXPERIENCE -> java.util.Optional.of("Consider enrolling in a pet training course before adoption");
            case YARD -> java.util.Optional.of("Plan regular visits to parks or open areas for exercise");
            case ACTIVITY -> java.util.Optional.of("Aim for at least 1-2 hours of daily activity for active breeds");
            case BUDGET -> java.util.Optional.of("Review monthly care costs and build a pet care budget");
            case WORK_SCHEDULE ->
                java.util.Optional.of("Consider pet sitters or daycare if you are away for long hours");
        };
    }

    private static void addSpeciesTips(Set<String> tips, PetContext pet) {
        switch (SpeciesKind.classify(pet.species())) {
            case DOG -> tips.add("Dogs benefit from consistent walks and positive-reinforcement training");
            case CAT -> tips.add("Cats need a quiet litter area and vertical spaces to feel secure");
            case OTHER -> {}
        }
    }

    private static void addProfileTips(Set<String> tips, PetContext pet, CompatibilityResult result) {
        if (pet.profile().needsYard()) {
            tips.add("This breed benefits from outdoor space or frequent off-leash exercise");
        }
        if (pet.profile().activityNeeds() >= 3) {
            int hours = pet.profile().activityNeeds() >= 4 ? 2 : 1;
            tips.add("Plan for " + hours + "-2 hours of daily exercise and mental enrichment");
        }
        if (pet.profile().careDifficulty() >= 3) {
            tips.add("Research breed-specific care requirements before committing");
        }
    }

    private static List<String> limitTips(Set<String> tips, int max) {
        List<String> list = new ArrayList<>(tips);
        return list.subList(0, Math.min(max, list.size()));
    }
}
