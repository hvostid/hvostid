package ru.hvostid.matching.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.hvostid.matching.client.ListingServiceClient;
import ru.hvostid.matching.client.ListingSummary;
import ru.hvostid.matching.config.CacheConfig;
import ru.hvostid.matching.dto.MatchScoreResponse;
import ru.hvostid.matching.dto.RecommendationItem;
import ru.hvostid.matching.dto.RecommendationsResponse;
import ru.hvostid.matching.entity.BuyerQuestionnaire;
import ru.hvostid.matching.exception.QuestionnaireRequiredException;

@Service
public class MatchRecommendationsService {
    private static final Logger log = LoggerFactory.getLogger(MatchRecommendationsService.class);

    /**
     * Cap on the number of PUBLISHED listings we will score per recommendations request.
     * Prevents an unbounded fan-out across listing-service / passport-service when the
     * catalog grows beyond a few hundred items.
     */
    static final int MAX_CANDIDATES = 200;

    /** Page size used when iterating through the listing-service catalog. */
    static final int CATALOG_PAGE_SIZE = 50;

    private final ListingServiceClient listingClient;
    private final MatchScoreService matchScoreService;

    public MatchRecommendationsService(ListingServiceClient listingClient, MatchScoreService matchScoreService) {
        this.listingClient = listingClient;
        this.matchScoreService = matchScoreService;
    }

    public RecommendationsResponse getRecommendations(long userId, int minScore, int page, int size, String requestId) {
        List<ScoredListing> sorted = scoredCandidates(userId, requestId);

        List<RecommendationItem> filtered = sorted.stream()
                .filter(item -> item.score() >= minScore)
                .map(item -> new RecommendationItem(
                        item.listing(), item.score(), item.response().level()))
                .toList();

        int totalElements = filtered.size();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int from = Math.min(page * size, totalElements);
        int to = Math.min(from + size, totalElements);
        List<RecommendationItem> pageContent = filtered.subList(from, to);

        log.info(
                "Recommendations userId={} minScore={} page={} size={} returned={}/{} requestId={}",
                userId,
                minScore,
                page,
                size,
                pageContent.size(),
                totalElements,
                requestId);

        return new RecommendationsResponse(pageContent, page, size, totalElements, totalPages);
    }

    /**
     * Builds the full sorted list of (listing, score) pairs for a buyer. Cached for the
     * 10-minute TTL configured on {@link CacheConfig#RECOMMENDATIONS_CACHE} so paging
     * through results does not refetch and rescore the catalog.
     */
    @Cacheable(cacheNames = CacheConfig.RECOMMENDATIONS_CACHE, key = "#userId")
    public List<ScoredListing> scoredCandidates(long userId, String requestId) {
        BuyerQuestionnaire questionnaire = matchScoreService
                .findQuestionnaire(userId)
                .orElseThrow(() -> new QuestionnaireRequiredException(
                        "Buyer questionnaire is required to compute recommendations. "
                                + "Submit one via POST /api/v1/match/questionnaire."));

        List<ListingSummary> candidates = fetchCandidates(requestId);
        List<ScoredListing> scored = new ArrayList<>(candidates.size());
        for (ListingSummary listing : candidates) {
            MatchScoreResponse response =
                    matchScoreService.scoreSnapshot(questionnaire, listing.toSnapshot(), requestId);
            scored.add(new ScoredListing(listing, response.score(), response));
        }
        scored.sort(Comparator.comparingInt(ScoredListing::score).reversed());
        return List.copyOf(scored);
    }

    private List<ListingSummary> fetchCandidates(String requestId) {
        List<ListingSummary> candidates = new ArrayList<>();
        int page = 0;
        while (candidates.size() < MAX_CANDIDATES) {
            ListingServiceClient.PublishedListingsPage pageResponse =
                    listingClient.getPublishedListings(page, CATALOG_PAGE_SIZE, requestId);
            for (ListingSummary listing : pageResponse.content()) {
                candidates.add(listing);
                if (candidates.size() >= MAX_CANDIDATES) {
                    break;
                }
            }
            if (pageResponse.content().isEmpty() || page + 1 >= pageResponse.totalPages()) {
                break;
            }
            page++;
        }
        return candidates;
    }

    /** Internal record for the cached sorted catalog. */
    public record ScoredListing(ListingSummary listing, int score, MatchScoreResponse response) {}
}
