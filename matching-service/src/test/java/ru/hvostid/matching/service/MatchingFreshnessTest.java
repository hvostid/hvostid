package ru.hvostid.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.hvostid.common.exception.ValidationException;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.matching.client.*;
import ru.hvostid.matching.dto.QuestionnaireRequest;
import ru.hvostid.matching.entity.*;
import ru.hvostid.matching.exception.ListingNotFoundException;
import ru.hvostid.matching.exception.ListingUnavailableException;
import ru.hvostid.matching.repository.BuyerQuestionnaireRepository;

@SpringBootTest(properties = "hvostid.recommendations.parallelism=4")
class MatchingFreshnessTest extends AbstractPostgresContainerTest {
    @Autowired
    private MatchScoreService scores;

    @Autowired
    private MatchRecommendationsService recommendations;

    @Autowired
    private QuestionnaireService questionnaires;

    @Autowired
    private BuyerQuestionnaireRepository repository;

    @MockitoBean
    private ListingServiceClient listings;

    @MockitoBean
    private PassportServiceClient passports;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        questionnaires.upsertQuestionnaire(request(20000, true, null, null), 42L);
    }

    @Test
    void readsNewQuestionnaireAndRemoteStateWithoutWaitingForTtl() {
        when(listings.getListing(1L, 42L, "fresh")).thenReturn(new ListingSnapshot(1L, "dog", "Husky", 12, "1"));
        when(passports.getPassport(1L, "fresh"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new PassportSnapshot("dog", "Husky", "friendly", null)));
        assertThat(scores.calculateScore(1L, 42L, "fresh").degraded()).isTrue();
        var healthy = scores.calculateScore(1L, 42L, "fresh");
        assertThat(healthy.degraded()).isFalse();
        questionnaires.upsertQuestionnaire(request(0, false, null, null), 42L);
        var changed = scores.calculateScore(1L, 42L, "fresh");
        assertThat(changed.score()).isLessThan(healthy.score());
        assertThat(changed.adaptationPlan()).hasSize(1);
        assertThat(changed.adaptationPlan().getFirst().dayRange()).isEqualTo("Before adoption");
        when(listings.getListing(1L, 42L, "fresh")).thenThrow(new ListingNotFoundException("Archived"));
        assertThatThrownBy(() -> scores.calculateScore(1L, 42L, "fresh")).isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void filtersSpeciesAndBreedBeforePassportCallsAndRefreshesSoldListings() {
        questionnaires.upsertQuestionnaire(request(20000, true, "CAT", "Siamese"), 42L);
        when(listings.getPublishedListings(0, 50, "preferences"))
                .thenReturn(page(
                        List.of(listing(1, "dog", "Husky"), listing(2, "cat", "Siamese"), listing(3, "cat", "Persian")),
                        1,
                        0));
        when(passports.getPassport(2L, "preferences"))
                .thenReturn(Optional.of(new PassportSnapshot("cat", "Siamese", "calm", null)));
        var result = recommendations.getRecommendations(42L, 0, 0, 20, "preferences");
        assertThat(result.content()).extracting(item -> item.listing().id()).containsExactly(2L);
        verify(passports, times(1)).getPassport(anyLong(), anyString());
        when(listings.getPublishedListings(0, 50, "preferences")).thenReturn(page(List.of(), 0, 0));
        assertThat(recommendations
                        .getRecommendations(42L, 0, 0, 20, "preferences")
                        .content())
                .isEmpty();
    }

    @Test
    void scoresCatalogBeyondTwoHundredWithBoundedSharedParallelism() throws Exception {
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        when(listings.getPublishedListings(anyInt(), eq(50), eq("large"))).thenAnswer(call -> {
            int page = call.getArgument(0);
            return page(
                    IntStream.range(page * 50 + 1, Math.min((page + 1) * 50 + 1, 252))
                            .mapToObj(id -> listing(id, "cat", "Siamese"))
                            .toList(),
                    6,
                    page);
        });
        when(passports.getPassport(anyLong(), eq("large"))).thenAnswer(call -> {
            int count = active.incrementAndGet();
            peak.accumulateAndGet(count, Math::max);
            try {
                TimeUnit.MILLISECONDS.sleep(3);
                return Optional.of(new PassportSnapshot("cat", "Siamese", "calm", null));
            } finally {
                active.decrementAndGet();
            }
        });
        long start = System.nanoTime();
        var result = recommendations.getRecommendations(42L, 0, 5, 50, "large");
        assertThat(result.totalElements()).isEqualTo(251);
        assertThat(result.content()).extracting(item -> item.listing().id()).containsExactly(251L);
        assertThat(peak.get()).isBetween(2, 4);
        assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start)).isLessThan(10);
        verify(passports, times(251)).getPassport(anyLong(), eq("large"));
        System.out.println("Matching cold catalog: candidates=251 parallelism=" + peak.get() + " durationMs="
                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }

    @Test
    void combinesConcurrentIdenticalRequestsButDoesNotKeepCompletedResults() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(listings.getPublishedListings(0, 50, "single-flight"))
                .thenReturn(page(List.of(listing(1, "cat", "Siamese")), 1, 0));
        when(passports.getPassport(1L, "single-flight")).thenAnswer(call -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return Optional.of(new PassportSnapshot("cat", "Siamese", "calm", null));
        });
        var first =
                CompletableFuture.supplyAsync(() -> recommendations.getRecommendations(42L, 0, 0, 20, "single-flight"));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var second =
                CompletableFuture.supplyAsync(() -> recommendations.getRecommendations(42L, 0, 0, 20, "single-flight"));
        // Wait until the follower has entered the service and is awaiting the shared future.
        Thread.sleep(150);
        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(second.get(5, TimeUnit.SECONDS));
        verify(listings, times(1)).getPublishedListings(0, 50, "single-flight");
        recommendations.getRecommendations(42L, 0, 0, 20, "single-flight");
        verify(listings, times(2)).getPublishedListings(0, 50, "single-flight");
    }

    @Test
    void maximumIntegerPageReturnsEmptyAndNegativePageIsRejected() {
        when(listings.getPublishedListings(0, 50, "pages"))
                .thenReturn(page(List.of(listing(1, "cat", "Siamese")), 1, 0));
        when(passports.getPassport(1L, "pages")).thenReturn(Optional.empty());
        var result = recommendations.getRecommendations(42L, 0, Integer.MAX_VALUE, 20, "pages");
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
        assertThatThrownBy(() -> recommendations.getRecommendations(42L, 0, -1, 20, "pages"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void candidateBudgetFailsExplicitlyInsteadOfReturningPartialResults() {
        var bounded = new MatchRecommendationsService(listings, scores, 4, 1, 200, java.time.Duration.ofSeconds(30));
        try {
            when(listings.getPublishedListings(anyInt(), eq(50), eq("bounded"))).thenAnswer(call -> {
                int page = call.getArgument(0);
                return page(
                        IntStream.range(page * 50 + 1, (page + 1) * 50 + 1)
                                .mapToObj(id -> listing(id, "cat", "Siamese"))
                                .toList(),
                        10,
                        page);
            });
            when(passports.getPassport(anyLong(), eq("bounded"))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> bounded.getRecommendations(42L, 0, 0, 20, "bounded"))
                    .isInstanceOf(ListingUnavailableException.class)
                    .hasMessageContaining("No partial ranking");
            verify(listings, times(5)).getPublishedListings(anyInt(), eq(50), eq("bounded"));
            verify(passports, times(200)).getPassport(anyLong(), eq("bounded"));
        } finally {
            bounded.close();
        }
    }

    @Test
    void timeoutStopsScoringAndDoesNotKeepFailedComputation() {
        var bounded = new MatchRecommendationsService(listings, scores, 4, 1, 10000, java.time.Duration.ofMillis(20));
        try {
            when(listings.getPublishedListings(0, 50, "timeout")).thenAnswer(call -> {
                TimeUnit.MILLISECONDS.sleep(40);
                return page(List.of(listing(1, "cat", "Siamese")), 1, 0);
            });
            assertThatThrownBy(() -> bounded.getRecommendations(42L, 0, 0, 20, "timeout"))
                    .isInstanceOf(ListingUnavailableException.class)
                    .hasMessageContaining("budget exceeded");
            verifyNoInteractions(passports);
            when(listings.getPublishedListings(0, 50, "timeout")).thenReturn(page(List.of(), 0, 0));
            assertThat(bounded.getRecommendations(42L, 0, 0, 20, "timeout").content())
                    .isEmpty();
        } finally {
            bounded.close();
        }
    }

    private static ListingSummary listing(long id, String species, String breed) {
        return new ListingSummary(
                id,
                10L,
                "Pet " + id,
                "Description",
                species,
                breed,
                12,
                1000,
                "City",
                Long.toString(id),
                Instant.EPOCH);
    }

    private static ListingServiceClient.PublishedListingsPage page(List<ListingSummary> content, int pages, int page) {
        return new ListingServiceClient.PublishedListingsPage(content, content.size(), pages, page, 50);
    }

    private static QuestionnaireRequest request(int budget, boolean ready, String species, String breed) {
        return new QuestionnaireRequest(
                LivingSpace.HOUSE,
                100,
                true,
                false,
                null,
                false,
                null,
                PetExperience.EXPERIENCED,
                ActivityLevel.HIGH,
                budget,
                WorkSchedule.HOME,
                ready,
                species,
                breed);
    }
}
