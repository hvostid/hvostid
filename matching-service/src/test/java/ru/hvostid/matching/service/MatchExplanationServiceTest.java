package ru.hvostid.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hvostid.matching.client.ListingSnapshot;
import ru.hvostid.matching.client.PassportSnapshot;
import ru.hvostid.matching.domain.CompatibilityResult;
import ru.hvostid.matching.domain.PetContext;
import ru.hvostid.matching.entity.*;

class MatchExplanationServiceTest {
    private MatchExplanationService service;
    private CompatibilityScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        service = new MatchExplanationService();
        calculator = new CompatibilityScoreCalculator();
    }

    @Test
    @DisplayName("summary is non-empty for a typical match")
    void buildSummary_typicalMatch_nonEmpty() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        PetContext pet = petContext("dog", "Labrador", "friendly, patient", true);
        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        String summary = service.buildSummary(questionnaire, pet, result, false);

        assertThat(summary).isNotBlank();
        assertThat(summary).containsIgnoringCase("match");
    }

    @Test
    @DisplayName("allergy conflict yields not recommended summary")
    void buildSummary_allergyConflict_notRecommended() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        questionnaire.setHasAllergies(true);
        questionnaire.setAllergyDetails("Severe dog dander allergy");
        PetContext pet = petContext("dog", "Husky", "active", true);
        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        String summary = service.buildSummary(questionnaire, pet, result, false);

        assertThat(summary).containsIgnoringCase("not recommended");
    }

    @Test
    @DisplayName("degraded flag adds passport caveat to summary")
    void buildSummary_degraded_mentionsPartialData() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        PetContext pet = petContext("dog", "Labrador", "friendly", true);
        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        String summary = service.buildSummary(questionnaire, pet, result, true);

        assertThat(summary).containsIgnoringCase("partial passport");
    }

    @Test
    @DisplayName("beginner with demanding breed yields experience-related tip")
    void buildTips_beginnerDemandingBreed_experienceTip() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        questionnaire.setPetExperience(PetExperience.BEGINNER);
        questionnaire.setLivingSpace(LivingSpace.APARTMENT);
        questionnaire.setLivingArea(40);
        PetContext pet = petContext("dog", "Husky", "active", true);
        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        var tips = service.buildTips(questionnaire, pet, result);

        assertThat(tips).isNotEmpty();
        assertThat(String.join(" ", tips)).containsIgnoringCase("training");
    }

    @Test
    @DisplayName("tips list is bounded and non-empty")
    void buildTips_returnsBoundedList() {
        BuyerQuestionnaire questionnaire = idealQuestionnaire();
        PetContext pet = petContext("dog", "Labrador", "friendly", true);
        CompatibilityResult result = calculator.calculate(questionnaire, pet);

        var tips = service.buildTips(questionnaire, pet, result);

        assertThat(tips).isNotEmpty();
        assertThat(tips.size()).isLessThanOrEqualTo(6);
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
