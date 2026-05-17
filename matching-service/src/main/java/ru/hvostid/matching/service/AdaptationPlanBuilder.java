package ru.hvostid.matching.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.hvostid.matching.domain.PetContext;
import ru.hvostid.matching.domain.SpeciesKind;
import ru.hvostid.matching.dto.AdaptationPhaseDto;

@Component
public class AdaptationPlanBuilder {

    private static final int PUPPY_MAX_AGE_MONTHS = 12;
    private static final int SENIOR_MIN_AGE_MONTHS = 84;

    public List<AdaptationPhaseDto> build(PetContext pet) {
        SpeciesKind species = SpeciesKind.classify(pet.species());
        AgeBand age = ageBand(pet.ageMonths());
        TemperamentBand temperament = temperamentBand(pet.temperament());
        boolean highActivity = pet.profile().activityNeeds() >= 3;

        return List.of(
                phaseOne(species, age, temperament),
                phaseTwo(species, age, temperament, highActivity),
                phaseThree(species, age, temperament));
    }

    private static AdaptationPhaseDto phaseOne(SpeciesKind species, AgeBand age, TemperamentBand temperament) {
        List<String> tasks = new ArrayList<>();
        switch (species) {
            case DOG -> {
                tasks.add("Set up a quiet corner with bed and water");
                tasks.add("Let the dog explore one room at a time at their own pace");
                tasks.add("Keep other pets separated until introductions are planned");
            }
            case CAT -> {
                tasks.add("Prepare a safe room with litter box, food, and hiding spots");
                tasks.add("Let the cat settle without forced handling");
                tasks.add("Keep other pets away from the safe room initially");
            }
            case OTHER -> {
                tasks.add("Prepare a secure enclosure or habitat");
                tasks.add("Allow the pet to acclimate without stress");
                tasks.add("Minimize loud noises and sudden movements");
            }
        }
        applyAgePhaseOne(tasks, age, species);
        applyTemperamentPhaseOne(tasks, temperament);
        return new AdaptationPhaseDto("1-3", "Getting to know each other", List.copyOf(tasks));
    }

    private static AdaptationPhaseDto phaseTwo(
            SpeciesKind species, AgeBand age, TemperamentBand temperament, boolean highActivity) {
        List<String> tasks = new ArrayList<>();
        switch (species) {
            case DOG -> {
                tasks.add("Establish a consistent feeding schedule");
                if (age == AgeBand.PUPPY) {
                    tasks.add("Offer frequent small meals throughout the day");
                    tasks.add("Start very short leash walks in quiet areas");
                } else if (age == AgeBand.SENIOR) {
                    tasks.add("Keep walks gentle and on a predictable schedule");
                } else {
                    tasks.add("Start short daily walks and build duration gradually");
                }
                tasks.add("Begin basic commands with positive reinforcement");
            }
            case CAT -> {
                tasks.add("Establish feeding times and litter box routine");
                tasks.add("Introduce scratching post and interactive toys");
                tasks.add("Spend short calm sessions nearby without forcing contact");
            }
            case OTHER -> {
                tasks.add("Establish regular feeding and cleaning routine");
                tasks.add("Observe daily habits and note any changes");
                tasks.add("Gradually extend supervised time outside the enclosure if appropriate");
            }
        }
        if (highActivity && species == SpeciesKind.DOG) {
            tasks.add("Add extra play or enrichment sessions between walks");
        }
        applyTemperamentPhaseTwo(tasks, temperament, species);
        return new AdaptationPhaseDto("4-7", "Building routine", List.copyOf(tasks));
    }

    private static AdaptationPhaseDto phaseThree(SpeciesKind species, AgeBand age, TemperamentBand temperament) {
        List<String> tasks = new ArrayList<>();
        switch (species) {
            case DOG -> {
                tasks.add("Introduce family members and visitors gradually");
                if (age == AgeBand.SENIOR) {
                    tasks.add("Keep socialization gentle and at a senior-friendly pace");
                } else {
                    tasks.add("Extend walk duration and explore new routes");
                }
                tasks.add("Schedule a vet checkup and discuss vaccination records");
            }
            case CAT -> {
                tasks.add("Allow gradual exploration of more rooms in the home");
                tasks.add("Introduce household members one at a time");
                tasks.add("Schedule a vet wellness visit");
            }
            case OTHER -> {
                tasks.add("Gradually increase supervised interaction time");
                tasks.add("Introduce enrichment appropriate for the species");
                tasks.add("Consult a vet or specialist for a health check");
            }
        }
        if (age == AgeBand.SENIOR) {
            tasks.add("Discuss senior care needs with your veterinarian");
        }
        applyTemperamentPhaseThree(tasks, temperament);
        return new AdaptationPhaseDto("8-14", "Socialization", List.copyOf(tasks));
    }

    private static void applyAgePhaseOne(List<String> tasks, AgeBand age, SpeciesKind species) {
        if (age == AgeBand.PUPPY && species == SpeciesKind.DOG) {
            tasks.add("Use frequent small meals and short potty breaks");
        }
        if (age == AgeBand.SENIOR) {
            tasks.add("Move at a gentle pace and avoid overstimulation");
        }
    }

    private static void applyTemperamentPhaseOne(List<String> tasks, TemperamentBand temperament) {
        if (temperament == TemperamentBand.NERVOUS) {
            tasks.add("Provide extra quiet time and avoid forced contact");
        }
    }

    private static void applyTemperamentPhaseTwo(List<String> tasks, TemperamentBand temperament, SpeciesKind species) {
        if (temperament == TemperamentBand.ACTIVE && species == SpeciesKind.DOG) {
            tasks.add("Include additional exercise or puzzle toys daily");
        }
    }

    private static void applyTemperamentPhaseThree(List<String> tasks, TemperamentBand temperament) {
        if (temperament == TemperamentBand.NERVOUS) {
            tasks.add("Continue slow introductions and respect the pet's comfort signals");
        }
    }

    private static AgeBand ageBand(Integer ageMonths) {
        if (ageMonths == null) {
            return AgeBand.ADULT;
        }
        if (ageMonths < PUPPY_MAX_AGE_MONTHS) {
            return AgeBand.PUPPY;
        }
        if (ageMonths >= SENIOR_MIN_AGE_MONTHS) {
            return AgeBand.SENIOR;
        }
        return AgeBand.ADULT;
    }

    private static TemperamentBand temperamentBand(String temperament) {
        if (temperament == null || temperament.isBlank()) {
            return TemperamentBand.NEUTRAL;
        }
        String t = temperament.toLowerCase(Locale.ROOT);
        if (containsAny(t, "nervous", "shy", "fearful", "anxious")) {
            return TemperamentBand.NERVOUS;
        }
        if (containsAny(t, "active", "energetic", "hyper")) {
            return TemperamentBand.ACTIVE;
        }
        return TemperamentBand.NEUTRAL;
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private enum AgeBand {
        PUPPY,
        ADULT,
        SENIOR
    }

    private enum TemperamentBand {
        NERVOUS,
        ACTIVE,
        NEUTRAL
    }
}
