package ru.hvostid.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hvostid.matching.client.ListingServiceClient;
import ru.hvostid.matching.client.ListingSnapshot;
import ru.hvostid.matching.client.PassportServiceClient;
import ru.hvostid.matching.client.PassportSnapshot;
import ru.hvostid.matching.dto.MatchScoreResponse;
import ru.hvostid.matching.entity.*;
import ru.hvostid.matching.exception.ListingNotFoundException;
import ru.hvostid.matching.exception.QuestionnaireNotFoundException;
import ru.hvostid.matching.repository.BuyerQuestionnaireRepository;

@ExtendWith(MockitoExtension.class)
class MatchScoreServiceTest {
    @Mock
    private BuyerQuestionnaireRepository questionnaireRepository;

    @Mock
    private ListingServiceClient listingClient;

    @Mock
    private PassportServiceClient passportClient;

    private MatchScoreService service;

    @BeforeEach
    void setUp() {
        service = new MatchScoreService(
                questionnaireRepository, listingClient, passportClient, new CompatibilityScoreCalculator());
    }

    @Test
    @DisplayName("happy path returns score response")
    void calculateScore_happyPath() {
        BuyerQuestionnaire questionnaire = questionnaire();
        when(questionnaireRepository.findByUserId(1L)).thenReturn(Optional.of(questionnaire));
        when(listingClient.getListing(42L, 1L, "req-1"))
                .thenReturn(new ListingSnapshot(42L, "dog", "Labrador", 12, "5"));
        when(passportClient.getPassport(5L, "req-1"))
                .thenReturn(Optional.of(new PassportSnapshot("dog", "Labrador", "friendly", null)));

        MatchScoreResponse response = service.calculateScore(42L, 1L, "req-1");

        assertThat(response.score()).isGreaterThan(0);
        assertThat(response.level()).isNotNull();
        assertThat(response.factors()).hasSize(8);
        assertThat(response.degraded()).isFalse();
    }

    @Test
    @DisplayName("passport unavailable sets degraded flag")
    void calculateScore_passportUnavailable_degraded() {
        when(questionnaireRepository.findByUserId(1L)).thenReturn(Optional.of(questionnaire()));
        when(listingClient.getListing(anyLong(), eq(1L), eq("req-1")))
                .thenReturn(new ListingSnapshot(1L, "dog", "Labrador", 12, "5"));
        when(passportClient.getPassport(5L, "req-1")).thenReturn(Optional.empty());

        MatchScoreResponse response = service.calculateScore(1L, 1L, "req-1");

        assertThat(response.degraded()).isTrue();
        assertThat(response.score()).isGreaterThan(0);
    }

    @Test
    @DisplayName("missing questionnaire throws")
    void calculateScore_noQuestionnaire_throws() {
        when(questionnaireRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateScore(1L, 1L, "req-1"))
                .isInstanceOf(QuestionnaireNotFoundException.class);
    }

    @Test
    @DisplayName("listing not found propagates")
    void calculateScore_listingNotFound_throws() {
        when(questionnaireRepository.findByUserId(1L)).thenReturn(Optional.of(questionnaire()));
        when(listingClient.getListing(99L, 1L, "req-1")).thenThrow(new ListingNotFoundException("not found"));

        assertThatThrownBy(() -> service.calculateScore(99L, 1L, "req-1")).isInstanceOf(ListingNotFoundException.class);
    }

    private static BuyerQuestionnaire questionnaire() {
        BuyerQuestionnaire q = new BuyerQuestionnaire(1L);
        q.setLivingSpace(LivingSpace.APARTMENT);
        q.setLivingArea(65);
        q.setHasYard(false);
        q.setHasChildren(false);
        q.setHasAllergies(false);
        q.setPetExperience(PetExperience.BEGINNER);
        q.setActivityLevel(ActivityLevel.MEDIUM);
        q.setMonthlyBudget(8000);
        q.setWorkSchedule(WorkSchedule.HYBRID);
        q.setReadyForAdaptation(true);
        return q;
    }
}
