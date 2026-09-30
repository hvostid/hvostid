package ru.hvostid.matching.service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.hvostid.common.exception.ValidationException;
import ru.hvostid.matching.client.ListingServiceClient;
import ru.hvostid.matching.client.ListingSummary;
import ru.hvostid.matching.domain.CompatibilityLevel;
import ru.hvostid.matching.domain.CompatibilityResult;
import ru.hvostid.matching.domain.SpeciesKind;
import ru.hvostid.matching.dto.QuestionnaireResponse;
import ru.hvostid.matching.dto.RecommendationItem;
import ru.hvostid.matching.dto.RecommendationsResponse;
import ru.hvostid.matching.entity.BuyerQuestionnaire;
import ru.hvostid.matching.exception.ListingUnavailableException;
import ru.hvostid.matching.exception.QuestionnaireRequiredException;

@Service
public class MatchRecommendationsService {
    static final int CATALOG_PAGE_SIZE = 50;
    private final ListingServiceClient listingClient;
    private final MatchScoreService matchScoreService;
    private final ThreadPoolExecutor workers;
    private final Semaphore computations;
    private final int maxScannedListings;
    private final Duration computationTimeout;
    private final ConcurrentHashMap<QuestionnaireResponse, CompletableFuture<List<ScoredListing>>> inFlight =
            new ConcurrentHashMap<>();

    public MatchRecommendationsService(
            ListingServiceClient listingClient,
            MatchScoreService matchScoreService,
            @Value("${hvostid.recommendations.parallelism:16}") int parallelism,
            @Value("${hvostid.recommendations.max-concurrent-computations:8}") int maxConcurrentComputations,
            @Value("${hvostid.recommendations.max-scanned-listings:10000}") int maxScannedListings,
            @Value("${hvostid.recommendations.computation-timeout:30s}") Duration computationTimeout) {
        this.listingClient = listingClient;
        this.matchScoreService = matchScoreService;
        this.maxScannedListings = Math.max(1, maxScannedListings);
        if (computationTimeout.isNegative() || computationTimeout.isZero()) {
            throw new IllegalArgumentException("Recommendation timeout must be positive");
        }
        this.computationTimeout = computationTimeout;
        computations = new Semaphore(Math.max(1, maxConcurrentComputations));
        int workerCount = Math.max(1, parallelism);
        workers = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(CATALOG_PAGE_SIZE, workerCount * maxConcurrentComputations)),
                Thread.ofVirtual().name("matching-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public RecommendationsResponse getRecommendations(long userId, int minScore, int page, int size, String requestId) {
        if (page < 0 || size < 1 || size > 50 || minScore < 0 || minScore > 100) {
            throw new ValidationException("Invalid recommendation page, size or minimum score");
        }
        List<RecommendationItem> filtered = scoredCandidates(userId, requestId).stream()
                .filter(item -> item.score() >= minScore)
                .map(item -> new RecommendationItem(item.listing(), item.score(), item.level()))
                .toList();
        int totalElements = filtered.size();
        int totalPages = (int) ((totalElements + (long) size - 1) / size);
        int from = (int) Math.min((long) page * size, totalElements);
        int to = (int) Math.min((long) from + size, totalElements);
        return new RecommendationsResponse(filtered.subList(from, to), page, size, totalElements, totalPages);
    }

    public List<ScoredListing> scoredCandidates(long userId, String requestId) {
        BuyerQuestionnaire questionnaire = matchScoreService
                .findQuestionnaire(userId)
                .orElseThrow(() -> new QuestionnaireRequiredException(
                        "Buyer questionnaire is required to compute recommendations. "
                                + "Submit one via POST /api/v1/match/questionnaire."));
        // Share only active work for an immutable questionnaire revision. Completed results
        // cannot be cached safely without versions/events from both remote services.
        QuestionnaireResponse key = QuestionnaireResponse.from(questionnaire);
        CompletableFuture<List<ScoredListing>> computation = new CompletableFuture<>();
        CompletableFuture<List<ScoredListing>> existing = inFlight.putIfAbsent(key, computation);
        if (existing != null) {
            return await(existing);
        }
        boolean acquired = computations.tryAcquire();
        try {
            if (!acquired) {
                throw new ListingUnavailableException("Matching capacity is busy; retry shortly");
            }
            List<ScoredListing> result = scoreCatalog(questionnaire, requestId);
            computation.complete(result);
            return result;
        } catch (RuntimeException | Error ex) {
            computation.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(key, computation);
            if (acquired) {
                computations.release();
            }
        }
    }

    private List<ScoredListing> scoreCatalog(BuyerQuestionnaire questionnaire, String requestId) {
        long deadline = System.nanoTime() + computationTimeout.toNanos();
        long scanned = 0;
        List<ScoredListing> scored = new ArrayList<>();
        var seen = new HashSet<Long>();
        for (int page = 0; ; page++) {
            remaining(deadline);
            ListingServiceClient.PublishedListingsPage response =
                    listingClient.getPublishedListings(page, CATALOG_PAGE_SIZE, requestId);
            scanned += response.content().size();
            if (scanned > maxScannedListings) {
                throw budgetExceeded();
            }
            remaining(deadline);
            List<ListingSummary> candidates = response.content().stream()
                    .filter(listing -> seen.add(listing.id()))
                    .filter(listing -> matchesPreferences(questionnaire, listing))
                    .toList();
            scored.addAll(scoreBatch(candidates, questionnaire, requestId, deadline));
            if (response.content().isEmpty() || (long) page + 1 >= response.totalPages()) {
                break;
            }
        }
        scored.sort(Comparator.comparingInt(ScoredListing::score)
                .reversed()
                .thenComparing(item -> item.listing().id()));
        return List.copyOf(scored);
    }

    private List<ScoredListing> scoreBatch(
            List<ListingSummary> candidates, BuyerQuestionnaire questionnaire, String requestId, long deadline) {
        List<Future<ScoredListing>> futures = new ArrayList<>();
        try {
            for (ListingSummary listing : candidates) {
                futures.add(workers.submit(() -> {
                    CompatibilityResult result =
                            matchScoreService.scoreSnapshot(questionnaire, listing.toSnapshot(), requestId);
                    return new ScoredListing(listing, result.score(), result.level());
                }));
            }
            List<ScoredListing> scored = new ArrayList<>(futures.size());
            for (Future<ScoredListing> future : futures) {
                scored.add(future.get(remaining(deadline), TimeUnit.NANOSECONDS));
            }
            return scored;
        } catch (RejectedExecutionException ex) {
            throw new ListingUnavailableException("Matching capacity is busy; retry shortly", ex);
        } catch (TimeoutException ex) {
            throw budgetExceeded();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ListingUnavailableException("Interrupted while computing recommendations", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new ListingUnavailableException("Failed to compute recommendations", ex.getCause());
        } finally {
            futures.forEach(future -> {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            });
        }
    }

    private static long remaining(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw budgetExceeded();
        }
        return remaining;
    }

    private static ListingUnavailableException budgetExceeded() {
        return new ListingUnavailableException(
                "Recommendation computation budget exceeded; retry later or use the catalog filters. No partial ranking was returned.");
    }

    static boolean matchesPreferences(BuyerQuestionnaire questionnaire, ListingSummary listing) {
        String species = questionnaire.getPreferredSpecies();
        if (species != null && !species.isBlank()) {
            SpeciesKind preferred = SpeciesKind.classify(species);
            if (preferred == SpeciesKind.OTHER) {
                if (!species.trim().equalsIgnoreCase(listing.species())) {
                    return false;
                }
            } else if (preferred != SpeciesKind.classify(listing.species())) {
                return false;
            }
        }
        String breed = questionnaire.getPreferredBreed();
        return breed == null
                || breed.isBlank()
                || (listing.breed() != null
                        && breed.trim().equalsIgnoreCase(listing.breed().trim()));
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ListingUnavailableException("Interrupted while computing recommendations", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new ListingUnavailableException("Failed to compute recommendations", ex.getCause());
        }
    }

    @PreDestroy
    public void close() {
        workers.shutdownNow();
    }

    public record ScoredListing(ListingSummary listing, int score, CompatibilityLevel level) {}
}
