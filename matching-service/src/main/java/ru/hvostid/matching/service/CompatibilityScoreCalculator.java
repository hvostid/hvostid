package ru.hvostid.matching.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.hvostid.matching.domain.*;
import ru.hvostid.matching.entity.*;

@Component
public class CompatibilityScoreCalculator {

    private static final int ALLERGY_CAP_MAX_TOTAL = 35;

    public CompatibilityResult calculate(BuyerQuestionnaire questionnaire, PetContext pet) {
        List<FactorScore> factors = new ArrayList<>();
        factors.add(scoreLivingSpace(questionnaire, pet));
        factors.add(scoreChildren(questionnaire, pet));
        FactorScore allergies = scoreAllergies(questionnaire, pet);
        factors.add(allergies);
        factors.add(scoreExperience(questionnaire, pet));
        factors.add(scoreYard(questionnaire, pet));
        factors.add(scoreActivity(questionnaire, pet));
        factors.add(scoreBudget(questionnaire, pet));
        factors.add(scoreWorkSchedule(questionnaire, pet));

        int rawTotal = factors.stream().mapToInt(FactorScore::score).sum();
        boolean allergyCap = allergies.score() <= 3 && Boolean.TRUE.equals(questionnaire.getHasAllergies());
        int total = allergyCap ? Math.min(rawTotal, ALLERGY_CAP_MAX_TOTAL) : rawTotal;
        CompatibilityLevel level = mapLevel(total, allergyCap);

        return new CompatibilityResult(total, level, List.copyOf(factors), allergyCap);
    }

    private FactorScore scoreLivingSpace(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.LIVING_SPACE.maxScore();
        LivingSpace space = q.getLivingSpace();
        int area = q.getLivingArea() == null ? 0 : q.getLivingArea();
        PetSize size = pet.profile().size();

        int score =
                switch (space) {
                    case APARTMENT ->
                        switch (size) {
                            case SMALL -> 18;
                            case MEDIUM -> area >= 50 ? 16 : area >= 40 ? 12 : 8;
                            case LARGE -> area >= 70 ? 8 : 4;
                        };
                    case HOUSE ->
                        switch (size) {
                            case SMALL, MEDIUM -> 19;
                            case LARGE -> Boolean.TRUE.equals(q.getHasYard()) ? 20 : 14;
                        };
                    case FARM -> 20;
                };

        String comment =
                switch (size) {
                    case SMALL -> "Living space is suitable for a small pet";
                    case MEDIUM -> "Apartment can work for a medium-sized pet with enough area";
                    case LARGE ->
                        space == LivingSpace.APARTMENT
                                ? "Large pets need more space than a typical apartment provides"
                                : "Living space fits a large pet";
                };
        return new FactorScore(CompatibilityFactor.LIVING_SPACE, clamp(score, max), comment);
    }

    private FactorScore scoreChildren(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.CHILDREN.maxScore();
        if (!Boolean.TRUE.equals(q.getHasChildren())) {
            return new FactorScore(
                    CompatibilityFactor.CHILDREN, max, "No children reported — no child-safety concerns");
        }

        if (!pet.passportAvailable()
                || pet.temperament() == null
                || pet.temperament().isBlank()) {
            int neutral = max * 2 / 3;
            return new FactorScore(
                    CompatibilityFactor.CHILDREN,
                    neutral,
                    "Passport temperament unavailable — moderate child compatibility assumed");
        }

        String temp = pet.temperament().toLowerCase(Locale.ROOT);
        int childAge = q.getChildrenAgeMin() == null ? 10 : q.getChildrenAgeMin();
        boolean youngChildren = childAge < 10;

        int score;
        String comment;
        if (containsAny(temp, "aggressive", "bite", "dominant", "guarding")) {
            score = youngChildren ? 3 : 6;
            comment = "Temperament may be challenging with children";
        } else if (containsAny(temp, "gentle", "patient", "calm", "friendly", "tolerant")) {
            score = youngChildren ? 14 : max;
            comment = "Temperament appears suitable for families with children";
        } else if (containsAny(temp, "nervous", "shy", "fearful")) {
            score = youngChildren ? 5 : 9;
            comment = "Nervous temperament may need careful introduction to children";
        } else {
            score = 10;
            comment = "Neutral temperament — supervise interactions with children";
        }
        return new FactorScore(CompatibilityFactor.CHILDREN, clamp(score, max), comment);
    }

    private FactorScore scoreAllergies(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.ALLERGIES.maxScore();
        if (!Boolean.TRUE.equals(q.getHasAllergies())) {
            return new FactorScore(CompatibilityFactor.ALLERGIES, max, "No allergies reported");
        }

        if (allergenConflictsWithPet(q.getAllergyDetails(), pet)) {
            if (pet.profile().hypoallergenic()) {
                return new FactorScore(
                        CompatibilityFactor.ALLERGIES,
                        10,
                        "Allergies reported but breed is often tolerated by allergy sufferers");
            }
            return new FactorScore(
                    CompatibilityFactor.ALLERGIES,
                    2,
                    "Reported allergies conflict with this species or breed — high risk");
        }

        return new FactorScore(
                CompatibilityFactor.ALLERGIES, 12, "Allergies reported but no direct conflict detected with this pet");
    }

    private FactorScore scoreExperience(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.EXPERIENCE.maxScore();
        int ownerLevel = experienceLevel(q.getPetExperience());
        int required = pet.profile().careDifficulty();
        int gap = required - ownerLevel;

        int score =
                switch (gap) {
                    case 0, -1, -2 -> max;
                    case 1 -> 10;
                    case 2 -> 6;
                    default -> 4;
                };

        String comment = gap >= 2
                ? "Beginner owner, this breed requires experienced handling"
                : gap == 1 ? "Some experience recommended for this breed" : "Owner experience matches breed care needs";
        return new FactorScore(CompatibilityFactor.EXPERIENCE, clamp(score, max), comment);
    }

    private FactorScore scoreYard(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.YARD.maxScore();
        if (!pet.profile().needsYard()) {
            return new FactorScore(CompatibilityFactor.YARD, max, "Breed does not require a yard");
        }
        if (Boolean.TRUE.equals(q.getHasYard())) {
            return new FactorScore(CompatibilityFactor.YARD, max, "Yard available for an active breed");
        }
        return new FactorScore(
                CompatibilityFactor.YARD, 2, "Active breed benefits from a yard, which is not available");
    }

    private FactorScore scoreActivity(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.ACTIVITY.maxScore();
        int owner = activityLevel(q.getActivityLevel());
        int needed = pet.profile().activityNeeds();
        int diff = Math.abs(owner - needed);

        int score =
                switch (diff) {
                    case 0 -> max;
                    case 1 -> 7;
                    default -> 3;
                };

        String comment = diff == 0
                ? "Owner activity level matches breed needs"
                : diff == 1
                        ? "Slight mismatch between owner activity and breed needs"
                        : "Significant activity level mismatch";
        return new FactorScore(CompatibilityFactor.ACTIVITY, clamp(score, max), comment);
    }

    private FactorScore scoreBudget(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.BUDGET.maxScore();
        int budget = q.getMonthlyBudget() == null ? 0 : q.getMonthlyBudget();
        int estimated = pet.profile().estimatedMonthlyCost();

        int score;
        String comment;
        if (budget >= estimated * 1.2) {
            score = max;
            comment = "Monthly budget comfortably covers estimated care costs";
        } else if (budget >= estimated) {
            score = 8;
            comment = "Budget meets estimated care costs with little margin";
        } else if (budget >= estimated * 0.7) {
            score = 5;
            comment = "Budget is below estimated care costs for this breed";
        } else {
            score = 2;
            comment = "Budget may be insufficient for ongoing care of this breed";
        }
        return new FactorScore(CompatibilityFactor.BUDGET, clamp(score, max), comment);
    }

    private FactorScore scoreWorkSchedule(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.WORK_SCHEDULE.maxScore();
        int attention = pet.profile().attentionNeeds();
        WorkSchedule schedule = q.getWorkSchedule();

        int score =
                switch (schedule) {
                    case HOME -> max;
                    case HYBRID -> attention >= 3 ? 3 : 4;
                    case OFFICE ->
                        switch (attention) {
                            case 1 -> 4;
                            case 2 -> 2;
                            default -> 1;
                        };
                };

        String comment = schedule == WorkSchedule.HOME
                ? "Home schedule supports pets needing attention"
                : attention >= 3
                        ? "Breed needs attention; office schedule may be challenging"
                        : "Work schedule is acceptable for this breed's attention needs";
        return new FactorScore(CompatibilityFactor.WORK_SCHEDULE, clamp(score, max), comment);
    }

    private CompatibilityLevel mapLevel(int total, boolean allergyCap) {
        if (allergyCap || total < 40) {
            return CompatibilityLevel.NOT_RECOMMENDED;
        }
        if (total >= 80) {
            return CompatibilityLevel.GREAT;
        }
        if (total >= 60) {
            return CompatibilityLevel.GOOD;
        }
        if (total >= 40) {
            return CompatibilityLevel.RISKY;
        }
        return CompatibilityLevel.NOT_RECOMMENDED;
    }

    private static boolean allergenConflictsWithPet(String allergyDetails, PetContext pet) {
        String details = allergyDetails == null ? "" : allergyDetails.toLowerCase(Locale.ROOT);
        String species = pet.species().toLowerCase(Locale.ROOT);
        boolean mentionsCats = containsAny(details, "cat", "кош", "feline", "dander");
        boolean mentionsDogs = containsAny(details, "dog", "собак", "canine", "dander", "fur", "hair");
        boolean mentionsGeneral = containsAny(details, "pet", "animal", "dander", "fur", "шерст", "пух", "аллерг");

        boolean isCat = species.contains("cat");
        boolean isDog = species.contains("dog");

        if (mentionsGeneral) {
            return true;
        }
        if (isCat && mentionsCats) {
            return true;
        }
        if (isDog && mentionsDogs) {
            return true;
        }
        return mentionsCats && mentionsDogs;
    }

    private static int experienceLevel(PetExperience experience) {
        if (experience == null) {
            return 0;
        }
        return switch (experience) {
            case NONE -> 0;
            case BEGINNER -> 1;
            case EXPERIENCED -> 2;
            case PROFESSIONAL -> 3;
        };
    }

    private static int activityLevel(ActivityLevel level) {
        if (level == null) {
            return 2;
        }
        return switch (level) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case VERY_HIGH -> 4;
        };
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int score, int max) {
        return Math.max(0, Math.min(score, max));
    }
}
