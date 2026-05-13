package ru.hvostid.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.hvostid.matching.entity.ActivityLevel;
import ru.hvostid.matching.entity.LivingSpace;
import ru.hvostid.matching.entity.PetExperience;
import ru.hvostid.matching.entity.WorkSchedule;

@Schema(description = "Buyer questionnaire used to compute compatibility with available listings")
public record QuestionnaireRequest(
        @NotNull(message = "Living space is required") @Schema(description = "Type of residence", example = "APARTMENT")
        LivingSpace livingSpace,

        @NotNull(message = "Living area is required")
        @Positive(message = "Living area must be positive")
        @Schema(description = "Living area in square meters", example = "55")
        Integer livingArea,

        @NotNull(message = "Has yard is required")
        @Schema(description = "Whether the residence has a private yard", example = "false")
        Boolean hasYard,

        @NotNull(message = "Has children is required")
        @Schema(description = "Whether children live in the household", example = "true")
        Boolean hasChildren,

        @PositiveOrZero(message = "Children age must be non-negative")
        @Schema(description = "Age of the youngest child in years (when hasChildren = true)", example = "6")
        Integer childrenAgeMin,

        @NotNull(message = "Has allergies is required")
        @Schema(description = "Whether anyone in the household has pet allergies", example = "false")
        Boolean hasAllergies,

        @Size(max = 2000, message = "Allergy details too long")
        @Schema(
                description = "Free-form allergy details (when hasAllergies = true)",
                example = "Mild reaction to cat dander, fine with short-hair breeds")
        String allergyDetails,

        @NotNull(message = "Pet experience is required")
        @Schema(description = "Prior experience with pets", example = "SOME")
        PetExperience petExperience,

        @NotNull(message = "Activity level is required")
        @Schema(description = "Desired activity level for the pet", example = "MEDIUM")
        ActivityLevel activityLevel,

        @NotNull(message = "Monthly budget is required")
        @PositiveOrZero(message = "Monthly budget must be non-negative")
        @Schema(description = "Monthly budget for pet care in rubles", example = "8000")
        Integer monthlyBudget,

        @NotNull(message = "Work schedule is required")
        @Schema(description = "Buyer's typical work schedule", example = "REMOTE")
        WorkSchedule workSchedule,

        @NotNull(message = "Ready for adaptation is required")
        @Schema(description = "Whether the buyer is prepared for an adaptation period", example = "true")
        Boolean readyForAdaptation,

        @Size(max = 255, message = "Preferred species too long")
        @Schema(description = "Preferred species (optional)", example = "CAT")
        String preferredSpecies,

        @Size(max = 255, message = "Preferred breed too long")
        @Schema(description = "Preferred breed (optional)", example = "Domestic shorthair")
        String preferredBreed) {}
