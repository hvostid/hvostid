package ru.hvostid.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import ru.hvostid.matching.entity.*;

@Schema(description = "Stored buyer questionnaire as returned by the matching API")
public record QuestionnaireResponse(
        @Schema(description = "Questionnaire identifier", example = "1")
        Long id,

        @Schema(description = "Owning user identifier", example = "42")
        Long userId,

        @Schema(description = "Type of residence", example = "APARTMENT")
        LivingSpace livingSpace,

        @Schema(description = "Living area in square meters", example = "55")
        Integer livingArea,

        @Schema(description = "Whether the residence has a private yard", example = "false")
        Boolean hasYard,

        @Schema(description = "Whether children live in the household", example = "true")
        Boolean hasChildren,

        @Schema(description = "Age of the youngest child in years", example = "6")
        Integer childrenAgeMin,

        @Schema(description = "Whether anyone in the household has pet allergies", example = "false")
        Boolean hasAllergies,

        @Schema(description = "Free-form allergy details") String allergyDetails,

        @Schema(description = "Prior experience with pets", example = "SOME")
        PetExperience petExperience,

        @Schema(description = "Desired activity level for the pet", example = "MEDIUM")
        ActivityLevel activityLevel,

        @Schema(description = "Monthly budget for pet care in rubles", example = "8000")
        Integer monthlyBudget,

        @Schema(description = "Buyer's typical work schedule", example = "REMOTE")
        WorkSchedule workSchedule,

        @Schema(description = "Whether the buyer is prepared for an adaptation period", example = "true")
        Boolean readyForAdaptation,

        @Schema(description = "Preferred species", example = "CAT")
        String preferredSpecies,

        @Schema(description = "Preferred breed", example = "Domestic shorthair")
        String preferredBreed,

        @Schema(description = "Creation timestamp", example = "2026-05-13T10:15:30Z")
        Instant createdAt,

        @Schema(description = "Last update timestamp", example = "2026-05-13T11:00:00Z")
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
