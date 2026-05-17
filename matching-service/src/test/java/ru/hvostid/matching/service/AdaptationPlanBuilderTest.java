package ru.hvostid.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hvostid.matching.client.ListingSnapshot;
import ru.hvostid.matching.client.PassportSnapshot;
import ru.hvostid.matching.domain.PetContext;
import ru.hvostid.matching.dto.AdaptationPhaseDto;

class AdaptationPlanBuilderTest {
    private AdaptationPlanBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AdaptationPlanBuilder();
    }

    @Test
    @DisplayName("plan has three phases with expected day ranges")
    void build_threePhases() {
        PetContext pet = petContext("dog", "Labrador", 24, "friendly", true);

        var plan = builder.build(pet);

        assertThat(plan).hasSize(3);
        assertThat(plan.get(0).dayRange()).isEqualTo("1-3");
        assertThat(plan.get(1).dayRange()).isEqualTo("4-7");
        assertThat(plan.get(2).dayRange()).isEqualTo("8-14");
        assertThat(plan.get(0).title()).isEqualTo("Getting to know each other");
        assertThat(plan.get(1).title()).isEqualTo("Building routine");
        assertThat(plan.get(2).title()).isEqualTo("Socialization");
    }

    @Test
    @DisplayName("dog and cat plans differ in tasks")
    void build_dogVsCat_differentTasks() {
        PetContext dog = petContext("dog", "Labrador", 24, "friendly", true);
        PetContext cat = petContext("cat", "British Shorthair", 24, "calm", true);

        String dogTasks = String.join(" ", flattenTasks(builder.build(dog)));
        String catTasks = String.join(" ", flattenTasks(builder.build(cat)));

        assertThat(dogTasks).contains("walk");
        assertThat(catTasks).contains("litter");
        assertThat(dogTasks).doesNotContain("litter");
        assertThat(catTasks).doesNotContain("leash walks");
    }

    @Test
    @DisplayName("puppy dog plan includes frequent small meals")
    void build_puppyDog_includesFrequentMeals() {
        PetContext puppy = petContext("dog", "Labrador", 6, "friendly", true);

        String tasks = String.join(" ", flattenTasks(builder.build(puppy)));

        assertThat(tasks).containsIgnoringCase("small meals");
    }

    @Test
    @DisplayName("senior plan includes gentle pace and senior vet care")
    void build_senior_includesSeniorCare() {
        PetContext senior = petContext("dog", "Labrador", 96, "calm", true);

        String tasks = String.join(" ", flattenTasks(builder.build(senior)));

        assertThat(tasks).containsIgnoringCase("gentle");
        assertThat(tasks).containsIgnoringCase("senior");
    }

    @Test
    @DisplayName("nervous temperament adds quiet-space guidance")
    void build_nervousTemperament_quietSpace() {
        PetContext nervous = petContext("dog", "Labrador", 24, "shy and fearful", true);

        String tasks = String.join(" ", flattenTasks(builder.build(nervous)));

        assertThat(tasks).containsIgnoringCase("quiet");
        assertThat(tasks).containsIgnoringCase("forced contact");
    }

    @Test
    @DisplayName("active temperament adds extra exercise in routine phase")
    void build_activeTemperament_extraExercise() {
        PetContext active = petContext("dog", "Husky", 24, "energetic and active", true);

        AdaptationPhaseDto routine = builder.build(active).get(1);
        String tasks = String.join(" ", routine.tasks());

        assertThat(tasks).containsIgnoringCase("exercise");
    }

    private static java.util.List<String> flattenTasks(java.util.List<AdaptationPhaseDto> plan) {
        return plan.stream().flatMap(p -> p.tasks().stream()).toList();
    }

    private static PetContext petContext(
            String species, String breed, int ageMonths, String temperament, boolean passport) {
        ListingSnapshot listing = new ListingSnapshot(1L, species, breed, ageMonths, "1");
        Optional<PassportSnapshot> passportOpt =
                passport ? Optional.of(new PassportSnapshot(species, breed, temperament, null)) : Optional.empty();
        return PetContext.from(listing, passportOpt);
    }
}
