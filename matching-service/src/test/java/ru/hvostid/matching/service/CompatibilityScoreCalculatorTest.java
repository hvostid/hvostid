package ru.hvostid.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.hvostid.matching.client.ListingSnapshot;
import ru.hvostid.matching.client.PassportSnapshot;
import ru.hvostid.matching.domain.CompatibilityFactor;
import ru.hvostid.matching.domain.CompatibilityLevel;
import ru.hvostid.matching.domain.CompatibilityResult;
import ru.hvostid.matching.domain.FactorScore;
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
        int factorSum = result.factors().stream().mapToInt(FactorScore::score).sum();
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
    @DisplayName("HOUSE with medium pet scores max living_space")
    void calculate_houseMediumPet_maxLivingSpace() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        PetContext pet = petContext("dog", "Labrador", "friendly", true);

        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        FactorScore livingSpace = factor(result, CompatibilityFactor.LIVING_SPACE);
        assertThat(livingSpace.score()).isEqualTo(20);
    }

    @ParameterizedTest(name = "attention={0} schedule={1} -> score={2}")
    @CsvSource({
        "1, HOME, 5",
        "1, HYBRID, 4",
        "1, OFFICE, 3",
        "2, HOME, 5",
        "2, HYBRID, 4",
        "2, OFFICE, 2",
        "3, HOME, 5",
        "3, HYBRID, 3",
        "3, OFFICE, 1"
    })
    @DisplayName("work schedule matrix matches documented scores")
    void workScheduleMatrix(int attention, WorkSchedule schedule, int expectedScore) {
        assertThat(CompatibilityScoreCalculator.workScheduleScore(attention, schedule))
                .isEqualTo(expectedScore);
    }

    @Test
    @DisplayName("allergy critical conflict applies cap via criticalConflict flag")
    void calculate_allergyCritical_capAppliedRegardlessOfScoreValue() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        questionnaire.setHasAllergies(true);
        questionnaire.setAllergyDetails("Severe dog dander allergy");

        PetContext pet = petContext("dog", "Husky", "active", true);

        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        FactorScore allergies = factor(result, CompatibilityFactor.ALLERGIES);
        assertThat(allergies.criticalConflict()).isTrue();
        assertThat(result.allergyCapApplied()).isTrue();
        assertThat(result.score()).isLessThanOrEqualTo(35);
        assertThat(result.level()).isEqualTo(CompatibilityLevel.NOT_RECOMMENDED);
    }

    @Test
    @DisplayName("unknown species uses neutral profile without large-yard penalty")
    void calculate_unknownSpecies_neutralProfile() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        questionnaire.setHasYard(false);
        PetContext pet = petContext("", "RareBreedXYZ", null, false);

        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        assertThat(pet.speciesUnknown()).isTrue();
        FactorScore yard = factor(result, CompatibilityFactor.YARD);
        assertThat(yard.score()).isEqualTo(10);
    }

    private static FactorScore factor(CompatibilityResult result, CompatibilityFactor factor) {
        return result.factors().stream()
                .filter(f -> f.factor() == factor)
                .findFirst()
                .orElseThrow();
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
