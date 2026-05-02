package ru.hvostid.matching.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hvostid.matching.dto.QuestionnaireRequest;
import ru.hvostid.matching.dto.QuestionnaireResponse;
import ru.hvostid.matching.entity.*;
import ru.hvostid.matching.exception.QuestionnaireNotFoundException;
import ru.hvostid.matching.repository.BuyerQuestionnaireRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionnaireServiceTest {
    @Mock
    private BuyerQuestionnaireRepository repository;

    private QuestionnaireService service;

    @BeforeEach
    void setUp() {
        service = new QuestionnaireService(repository);
    }

    private QuestionnaireRequest validRequest() {
        return new QuestionnaireRequest(
                LivingSpace.APARTMENT,
                65,
                false,
                true,
                7,
                false,
                null,
                PetExperience.BEGINNER,
                ActivityLevel.MEDIUM,
                5000,
                WorkSchedule.HYBRID,
                true,
                "dog",
                null
        );
    }

    @Nested
    @DisplayName("upsertQuestionnaire")
    class UpsertTests {
        @Test
        @DisplayName("creates a new questionnaire when none exists")
        void upsert_createsWhenAbsent() {
            when(repository.findByUserId(1L)).thenReturn(Optional.empty());
            when(repository.save(any(BuyerQuestionnaire.class)))
                    .thenAnswer(inv -> {
                        BuyerQuestionnaire q = inv.getArgument(0);
                        q.setId(42L);
                        return q;
                    });

            QuestionnaireResponse response = service.upsertQuestionnaire(validRequest(), 1L);

            assertThat(response.id()).isEqualTo(42L);
            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.livingSpace()).isEqualTo(LivingSpace.APARTMENT);
            assertThat(response.livingArea()).isEqualTo(65);
            assertThat(response.petExperience()).isEqualTo(PetExperience.BEGINNER);
            assertThat(response.preferredSpecies()).isEqualTo("dog");
            verify(repository).save(any(BuyerQuestionnaire.class));
        }

        @Test
        @DisplayName("updates existing questionnaire instead of creating duplicate")
        void upsert_updatesWhenPresent() {
            BuyerQuestionnaire existing = new BuyerQuestionnaire(1L);
            existing.setId(10L);
            existing.setLivingSpace(LivingSpace.HOUSE);
            existing.setLivingArea(120);
            existing.setMonthlyBudget(8000);

            when(repository.findByUserId(1L)).thenReturn(Optional.of(existing));
            when(repository.save(any(BuyerQuestionnaire.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            QuestionnaireResponse response = service.upsertQuestionnaire(validRequest(), 1L);

            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.livingSpace()).isEqualTo(LivingSpace.APARTMENT);
            assertThat(response.livingArea()).isEqualTo(65);
            assertThat(response.monthlyBudget()).isEqualTo(5000);

            ArgumentCaptor<BuyerQuestionnaire> captor = ArgumentCaptor.forClass(BuyerQuestionnaire.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("trims whitespace on optional string fields")
        void upsert_trimsStrings() {
            QuestionnaireRequest request = new QuestionnaireRequest(
                    LivingSpace.HOUSE, 100, true, false, null, true, "  cats   ",
                    PetExperience.EXPERIENCED, ActivityLevel.HIGH, 10000,
                    WorkSchedule.HOME, true, "  dog  ", "  Labrador  "
            );

            when(repository.findByUserId(2L)).thenReturn(Optional.empty());
            when(repository.save(any(BuyerQuestionnaire.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            QuestionnaireResponse response = service.upsertQuestionnaire(request, 2L);

            assertThat(response.allergyDetails()).isEqualTo("cats");
            assertThat(response.preferredSpecies()).isEqualTo("dog");
            assertThat(response.preferredBreed()).isEqualTo("Labrador");
        }
    }

    @Nested
    @DisplayName("getQuestionnaire")
    class GetTests {

        @Test
        @DisplayName("returns the user's questionnaire when present")
        void get_returnsWhenPresent() {
            BuyerQuestionnaire q = new BuyerQuestionnaire(5L);
            q.setId(99L);
            q.setLivingSpace(LivingSpace.FARM);
            q.setLivingArea(500);
            q.setHasYard(true);
            q.setHasChildren(false);
            q.setHasAllergies(false);
            q.setPetExperience(PetExperience.PROFESSIONAL);
            q.setActivityLevel(ActivityLevel.VERY_HIGH);
            q.setMonthlyBudget(50000);
            q.setWorkSchedule(WorkSchedule.HOME);
            q.setReadyForAdaptation(true);

            when(repository.findByUserId(5L)).thenReturn(Optional.of(q));

            QuestionnaireResponse response = service.getQuestionnaire(5L);

            assertThat(response.id()).isEqualTo(99L);
            assertThat(response.livingSpace()).isEqualTo(LivingSpace.FARM);
            assertThat(response.activityLevel()).isEqualTo(ActivityLevel.VERY_HIGH);
        }

        @Test
        @DisplayName("throws QuestionnaireNotFoundException when missing")
        void get_throwsWhenMissing() {
            when(repository.findByUserId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getQuestionnaire(99L))
                    .isInstanceOf(QuestionnaireNotFoundException.class)
                    .hasMessageContaining("not found");
            verify(repository, never()).save(any());
        }
    }
}
