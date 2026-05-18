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
    private static final int LEVEL_GREAT_MIN = 80;
    private static final int LEVEL_GOOD_MIN = 60;
    private static final int LEVEL_RISKY_MIN = 40;
    private static final int YOUNG_CHILD_AGE_THRESHOLD = 10;

    /**
     * Work-schedule scores by attention need (rows 1..3) and schedule column index (HOME=0, HYBRID=1,
     * OFFICE=2). HYBRID scores above OFFICE because the owner is home part of the week; OFFICE leaves
     * the pet alone longest.
     */
    private static final int[][] WORK_SCHEDULE_MATRIX = {
        {5, 4, 3},
        {5, 4, 2},
        {5, 3, 1}
    };

    public CompatibilityResult calculate(BuyerQuestionnaire questionnaire, PetContext pet) {
        List<FactorScore> factors = new ArrayList<>();
        factors.add(scoreLivingSpace(questionnaire, pet));
        factors.add(scoreChildren(questionnaire, pet));
        factors.add(scoreAllergies(questionnaire, pet));
        factors.add(scoreExperience(questionnaire, pet));
        factors.add(scoreYard(questionnaire, pet));
        factors.add(scoreActivity(questionnaire, pet));
        factors.add(scoreBudget(questionnaire, pet));
        factors.add(scoreWorkSchedule(questionnaire, pet));

        int rawTotal = factors.stream().mapToInt(FactorScore::score).sum();
        boolean allergyCap = factors.stream().anyMatch(FactorScore::criticalConflict);
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
                            case SMALL, MEDIUM -> max;
                            case LARGE -> Boolean.TRUE.equals(q.getHasYard()) ? max : 14;
                        };
                    case FARM -> max;
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
        return FactorScore.of(CompatibilityFactor.LIVING_SPACE, Math.clamp(score, 0, max), comment);
    }

    private FactorScore scoreChildren(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.CHILDREN.maxScore();
        if (!Boolean.TRUE.equals(q.getHasChildren())) {
            return FactorScore.of(CompatibilityFactor.CHILDREN, max, "No children reported - no child-safety concerns");
        }

        if (!pet.passportAvailable()
                || pet.temperament() == null
                || pet.temperament().isBlank()) {
            int neutral = max * 2 / 3;
            return FactorScore.of(
                    CompatibilityFactor.CHILDREN,
                    neutral,
                    "Passport temperament unavailable - moderate child compatibility assumed");
        }

        String temp = pet.temperament().toLowerCase(Locale.ROOT);
        int childAge = q.getChildrenAgeMin() == null ? YOUNG_CHILD_AGE_THRESHOLD : q.getChildrenAgeMin();
        boolean youngChildren = childAge < YOUNG_CHILD_AGE_THRESHOLD;

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
            comment = "Neutral temperament - supervise interactions with children";
        }
        return FactorScore.of(CompatibilityFactor.CHILDREN, Math.clamp(score, 0, max), comment);
    }

    private FactorScore scoreAllergies(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.ALLERGIES.maxScore();
        if (!Boolean.TRUE.equals(q.getHasAllergies())) {
            return FactorScore.of(CompatibilityFactor.ALLERGIES, max, "No allergies reported");
        }

        if (allergenConflictsWithPet(q.getAllergyDetails(), pet)) {
            if (pet.profile().hypoallergenic()) {
                return FactorScore.of(
                        CompatibilityFactor.ALLERGIES,
                        10,
                        "Allergies reported but breed is often tolerated by allergy sufferers");
            }
            return FactorScore.critical(
                    CompatibilityFactor.ALLERGIES,
                    2,
                    "Reported allergies conflict with this species or breed - high risk");
        }

        return FactorScore.of(
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
        return FactorScore.of(CompatibilityFactor.EXPERIENCE, Math.clamp(score, 0, max), comment);
    }

    private FactorScore scoreYard(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.YARD.maxScore();
        if (!pet.profile().needsYard()) {
            return FactorScore.of(CompatibilityFactor.YARD, max, "Breed does not require a yard");
        }
        if (Boolean.TRUE.equals(q.getHasYard())) {
            return FactorScore.of(CompatibilityFactor.YARD, max, "Yard available for an active breed");
        }
        return FactorScore.of(CompatibilityFactor.YARD, 2, "Active breed benefits from a yard, which is not available");
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
        return FactorScore.of(CompatibilityFactor.ACTIVITY, Math.clamp(score, 0, max), comment);
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
        return FactorScore.of(CompatibilityFactor.BUDGET, Math.clamp(score, 0, max), comment);
    }

    private FactorScore scoreWorkSchedule(BuyerQuestionnaire q, PetContext pet) {
        int max = CompatibilityFactor.WORK_SCHEDULE.maxScore();
        int attention = clampAttentionIndex(pet.profile().attentionNeeds());
        int scheduleIndex = workScheduleIndex(q.getWorkSchedule());
        int score = WORK_SCHEDULE_MATRIX[attention][scheduleIndex];

        String comment =
                switch (q.getWorkSchedule()) {
                    case HOME -> "Home schedule supports pets needing attention";
                    case HYBRID ->
                        attention >= 2
                                ? "Hybrid schedule is better than full office for attentive breeds"
                                : "Hybrid schedule is acceptable for this breed's attention needs";
                    case OFFICE ->
                        attention >= 2
                                ? "Breed needs attention; office schedule may be challenging"
                                : "Office schedule is acceptable for this breed's attention needs";
                };
        return FactorScore.of(CompatibilityFactor.WORK_SCHEDULE, Math.clamp(score, 0, max), comment);
    }

    static int workScheduleScore(int attentionNeeds, WorkSchedule schedule) {
        int attention = clampAttentionIndex(attentionNeeds);
        return WORK_SCHEDULE_MATRIX[attention][workScheduleIndex(schedule)];
    }

    private static int clampAttentionIndex(int attentionNeeds) {
        return Math.clamp(attentionNeeds - 1, 0, 2);
    }

    private static int workScheduleIndex(WorkSchedule schedule) {
        return switch (schedule) {
            case HOME -> 0;
            case HYBRID -> 1;
            case OFFICE -> 2;
        };
    }

    private CompatibilityLevel mapLevel(int total, boolean allergyCap) {
        if (allergyCap || total < LEVEL_RISKY_MIN) {
            return CompatibilityLevel.NOT_RECOMMENDED;
        }
        if (total >= LEVEL_GREAT_MIN) {
            return CompatibilityLevel.GREAT;
        }
        if (total >= LEVEL_GOOD_MIN) {
            return CompatibilityLevel.GOOD;
        }
        return CompatibilityLevel.RISKY;
    }

    private static boolean allergenConflictsWithPet(String allergyDetails, PetContext pet) {
        String details = allergyDetails == null ? "" : allergyDetails.toLowerCase(Locale.ROOT);
        if (containsAny(details, "pet", "animal", "dander", "fur", "шерст", "пух", "аллерг")) {
            return true;
        }
        return switch (SpeciesKind.classify(pet.species())) {
            case CAT -> containsAny(details, "cat", "кош", "feline", "dander");
            case DOG -> containsAny(details, "dog", "собак", "canine", "dander", "fur", "hair");
            case OTHER -> false;
        };
    }

    private static int experienceLevel(PetExperience experience) {
        return switch (experience) {
            case NONE -> 0;
            case BEGINNER -> 1;
            case EXPERIENCED -> 2;
            case PROFESSIONAL -> 3;
        };
    }

    private static int activityLevel(ActivityLevel level) {
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
}
