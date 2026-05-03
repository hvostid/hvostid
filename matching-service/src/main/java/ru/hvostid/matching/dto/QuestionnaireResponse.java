package ru.hvostid.matching.dto;

import java.time.Instant;
import ru.hvostid.matching.entity.*;

public record QuestionnaireResponse(
        Long id,
        Long userId,
        LivingSpace livingSpace,
        Integer livingArea,
        Boolean hasYard,
        Boolean hasChildren,
        Integer childrenAgeMin,
        Boolean hasAllergies,
        String allergyDetails,
        PetExperience petExperience,
        ActivityLevel activityLevel,
        Integer monthlyBudget,
        WorkSchedule workSchedule,
        Boolean readyForAdaptation,
        String preferredSpecies,
        String preferredBreed,
        Instant createdAt,
        Instant updatedAt) {
    public static QuestionnaireResponse from(BuyerQuestionnaire q) {
        return new QuestionnaireResponse(
                q.getId(),
                q.getUserId(),
                q.getLivingSpace(),
                q.getLivingArea(),
                q.getHasYard(),
                q.getHasChildren(),
                q.getChildrenAgeMin(),
                q.getHasAllergies(),
                q.getAllergyDetails(),
                q.getPetExperience(),
                q.getActivityLevel(),
                q.getMonthlyBudget(),
                q.getWorkSchedule(),
                q.getReadyForAdaptation(),
                q.getPreferredSpecies(),
                q.getPreferredBreed(),
                q.getCreatedAt(),
                q.getUpdatedAt());
    }
}
