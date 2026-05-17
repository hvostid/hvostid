package ru.hvostid.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hvostid.matching.client.ListingServiceClient;
import ru.hvostid.matching.client.ListingSnapshot;
import ru.hvostid.matching.client.ListingSummary;
import ru.hvostid.matching.domain.CompatibilityLevel;
import ru.hvostid.matching.domain.CompatibilityResult;
import ru.hvostid.matching.dto.RecommendationsResponse;
import ru.hvostid.matching.entity.BuyerQuestionnaire;
import ru.hvostid.matching.exception.QuestionnaireRequiredException;

@ExtendWith(MockitoExtension.class)
class MatchRecommendationsServiceTest {
    @Mock
    private ListingServiceClient listingClient;

    @Mock
    private MatchScoreService matchScoreService;

    private MatchRecommendationsService service;
    private BuyerQuestionnaire questionnaire;

    @BeforeEach
    void setUp() {
        service = new MatchRecommendationsService(listingClient, matchScoreService);
        questionnaire = new BuyerQuestionnaire(1L);
    }

    @Test
    @DisplayName("returns 400-bound exception when buyer has no questionnaire")
    void recommendations_noQuestionnaire_throws() {
        when(matchScoreService.findQuestionnaire(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRecommendations(1L, 40, 0, 10, "req-1"))
                .isInstanceOf(QuestionnaireRequiredException.class);
    }

    @Test
    @DisplayName("sorts by score descending and filters by minScore")
    void recommendations_sortAndFilter() {
        when(matchScoreService.findQuestionnaire(1L)).thenReturn(Optional.of(questionnaire));
        when(listingClient.getPublishedListings(eq(0), anyInt(), eq("req-1")))
                .thenReturn(page(listings(10, 30, 60, 90)));
        // No additional pages: totalPages=1
        stubScores(10, 30, 60, 90);

        RecommendationsResponse result = service.getRecommendations(1L, 50, 0, 10, "req-1");

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).score()).isEqualTo(90);
        assertThat(result.content().get(1).score()).isEqualTo(60);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("respects requested page and size")
    void recommendations_pagination() {
        when(matchScoreService.findQuestionnaire(1L)).thenReturn(Optional.of(questionnaire));
        when(listingClient.getPublishedListings(eq(0), anyInt(), eq("req-1")))
                .thenReturn(page(listings(95, 90, 85, 80, 75, 70, 65)));
        stubScores(95, 90, 85, 80, 75, 70, 65);

        RecommendationsResponse firstPage = service.getRecommendations(1L, 0, 0, 3, "req-1");
        RecommendationsResponse secondPage = service.getRecommendations(1L, 0, 1, 3, "req-1");

        assertThat(firstPage.content().stream().map(item -> item.score()).toList())
                .containsExactly(95, 90, 85);
        assertThat(secondPage.content().stream().map(item -> item.score()).toList())
                .containsExactly(80, 75, 70);
        assertThat(firstPage.totalElements()).isEqualTo(7);
        assertThat(firstPage.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("uses default minScore=40 when caller passes 0 of those below threshold")
    void recommendations_minScoreFilter_keepsOnlyAtOrAbove() {
        when(matchScoreService.findQuestionnaire(1L)).thenReturn(Optional.of(questionnaire));
        when(listingClient.getPublishedListings(eq(0), anyInt(), eq("req-1")))
                .thenReturn(page(listings(20, 40, 41, 39)));
        stubScores(20, 40, 41, 39);

        RecommendationsResponse result = service.getRecommendations(1L, 40, 0, 10, "req-1");

        assertThat(result.content().stream().map(item -> item.score()).toList()).containsExactly(41, 40);
    }

    @Test
    @DisplayName("empty catalog returns empty page")
    void recommendations_emptyCatalog() {
        when(matchScoreService.findQuestionnaire(1L)).thenReturn(Optional.of(questionnaire));
        when(listingClient.getPublishedListings(eq(0), anyInt(), eq("req-1"))).thenReturn(emptyPage());

        RecommendationsResponse result = service.getRecommendations(1L, 0, 0, 10, "req-1");

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    private void stubScores(int... scores) {
        for (int score : scores) {
            String passportId = "p-" + score;
            ListingSnapshot snapshot = new ListingSnapshot((long) score, "dog", "Labrador", 12, passportId);
            CompatibilityResult result = new CompatibilityResult(score, levelFor(score), List.of(), false);
            when(matchScoreService.scoreSnapshot(eq(questionnaire), eq(snapshot), eq("req-1")))
                    .thenReturn(result);
        }
    }

    private static CompatibilityLevel levelFor(int score) {
        if (score >= 80) return CompatibilityLevel.GREAT;
        if (score >= 60) return CompatibilityLevel.GOOD;
        if (score >= 40) return CompatibilityLevel.RISKY;
        return CompatibilityLevel.NOT_RECOMMENDED;
    }

    private static List<ListingSummary> listings(int... scores) {
        List<ListingSummary> out = new ArrayList<>();
        for (int s : scores) {
            out.add(new ListingSummary(
                    (long) s,
                    10L,
                    "Listing " + s,
                    "Desc",
                    "dog",
                    "Labrador",
                    12,
                    10000,
                    "Moscow",
                    "p-" + s,
                    Instant.now()));
        }
        return out;
    }

    private static ListingServiceClient.PublishedListingsPage page(List<ListingSummary> content) {
        return new ListingServiceClient.PublishedListingsPage(content, content.size(), 1, 0, content.size());
    }

    private static ListingServiceClient.PublishedListingsPage emptyPage() {
        return new ListingServiceClient.PublishedListingsPage(List.of(), 0, 0, 0, 0);
    }
}
