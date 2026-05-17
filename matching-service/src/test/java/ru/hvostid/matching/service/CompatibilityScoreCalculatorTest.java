package ru.hvostid.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hvostid.matching.client.ListingSnapshot;
import ru.hvostid.matching.client.PassportSnapshot;
import ru.hvostid.matching.domain.CompatibilityFactor;
import ru.hvostid.matching.domain.CompatibilityLevel;
import ru.hvostid.matching.domain.CompatibilityResult;
import ru.hvostid.matching.domain.PetContext;
import ru.hvostid.matching.entity.*;

class CompatibilityScoreCalculatorTest {
    private CompatibilityScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CompatibilityScoreCalculator();
    }

    @Test
    @DisplayName("returns all eight factors and score equals sum of factor scores")
    void calculate_allFactors_sumMatchesTotal() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        PetContext pet = petContext("dog", "Labrador", "friendly, gentle", true);

        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        assertThat(result.factors()).hasSize(8);
        assertThat(result.factors().stream().map(f -> f.factor().apiName()))
                .containsExactlyInAnyOrder(
                        "living_space",
                        "children",
                        "allergies",
                        "experience",
                        "yard",
                        "activity",
                        "budget",
                        "work_schedule");
        int factorSum = result.factors().stream().mapToInt(f -> f.score()).sum();
        assertThat(result.score()).isEqualTo(factorSum);
    }

    @Test
    @DisplayName("ideal match yields GREAT level")
    void calculate_idealMatch_greatLevel() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        PetContext pet = petContext("dog", "Labrador", "friendly, patient", true);

        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        assertThat(result.level()).isEqualTo(CompatibilityLevel.GREAT);
        assertThat(result.score()).isGreaterThanOrEqualTo(80);
        assertThat(result.allergyCapApplied()).isFalse();
    }

    @Test
    @DisplayName("allergy conflict caps score and yields NOT_RECOMMENDED")
    void calculate_catAllergyWithDog_notRecommended() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        questionnaire.setHasAllergies(true);
        questionnaire.setAllergyDetails("Severe dog dander allergy");
        questionnaire.setPetExperience(PetExperience.BEGINNER);

        PetContext pet = petContext("dog", "Husky", "active", true);

        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        assertThat(result.allergyCapApplied()).isTrue();
        assertThat(result.score()).isLessThanOrEqualTo(35);
        assertThat(result.level()).isEqualTo(CompatibilityLevel.NOT_RECOMMENDED);
        assertThat(result.factors().stream()
                        .filter(f -> f.factor() == CompatibilityFactor.ALLERGIES)
                        .findFirst()
                        .orElseThrow()
                        .score())
                .isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("unknown breed uses default profile without error")
    void calculate_unknownBreed_usesDefault() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        PetContext pet = petContext("dog", "RareBreedXYZ", null, false);

        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        assertThat(result.factors()).hasSize(8);
        assertThat(result.score()).isGreaterThan(0);
    }

    private static BuyerQuestionnaire idealQuestionnaire() {
        BuyerQuestionnaire q = new BuyerQuestionnaire(1L);
        q.setLivingSpace(LivingSpace.HOUSE);
        q.setLivingArea(120);
        q.setHasYard(true);
        q.setHasChildren(false);
        q.setHasAllergies(false);
        q.setPetExperience(PetExperience.EXPERIENCED);
        q.setActivityLevel(ActivityLevel.MEDIUM);
        q.setMonthlyBudget(15000);
        q.setWorkSchedule(WorkSchedule.HOME);
        q.setReadyForAdaptation(true);
        return q;
    }

    private static PetContext petContext(String species, String breed, String temperament, boolean passport) {
        ListingSnapshot listing = new ListingSnapshot(1L, species, breed, 24, "1");
        Optional<PassportSnapshot> passportOpt =
                passport ? Optional.of(new PassportSnapshot(species, breed, temperament, null)) : Optional.empty();
        return PetContext.from(listing, passportOpt);
    }
}
