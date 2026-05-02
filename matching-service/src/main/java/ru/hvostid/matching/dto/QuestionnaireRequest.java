package ru.hvostid.matching.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.hvostid.matching.entity.ActivityLevel;
import ru.hvostid.matching.entity.LivingSpace;
import ru.hvostid.matching.entity.PetExperience;
import ru.hvostid.matching.entity.WorkSchedule;

public record QuestionnaireRequest(
        @NotNull(message = "Living space is required")
        LivingSpace livingSpace,

        @NotNull(message = "Living area is required")
        @Positive(message = "Living area must be positive")
        Integer livingArea,

        @NotNull(message = "Has yard is required")
        Boolean hasYard,

        @NotNull(message = "Has children is required")
        Boolean hasChildren,

        @PositiveOrZero(message = "Children age must be non-negative")
        Integer childrenAgeMin,

        @NotNull(message = "Has allergies is required")
        Boolean hasAllergies,

        @Size(max = 2000, message = "Allergy details too long")
        String allergyDetails,

        @NotNull(message = "Pet experience is required")
        PetExperience petExperience,

        @NotNull(message = "Activity level is required")
        ActivityLevel activityLevel,

        @NotNull(message = "Monthly budget is required")
        @PositiveOrZero(message = "Monthly budget must be non-negative")
        Integer monthlyBudget,

        @NotNull(message = "Work schedule is required")
        WorkSchedule workSchedule,

        @NotNull(message = "Ready for adaptation is required")
        Boolean readyForAdaptation,

        @Size(max = 255, message = "Preferred species too long")
        String preferredSpecies,

        @Size(max = 255, message = "Preferred breed too long")
        String preferredBreed
) {
}
