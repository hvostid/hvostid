package ru.hvostid.matching.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.hvostid.matching.exception.ListingNotFoundException;
import ru.hvostid.matching.exception.ListingUnavailableException;

@Component
public class ListingServiceClient {
    private static final Logger log = LoggerFactory.getLogger(ListingServiceClient.class);

    private final RestClient listingRestClient;

    public ListingServiceClient(RestClient listingRestClient) {
        this.listingRestClient = listingRestClient;
    }

    @CircuitBreaker(name = "listingService", fallbackMethod = "getListingFallback")
    public ListingSnapshot getListing(long listingId, long userId, String requestId) {
        HttpHeaders headers = ServiceClientHeaders.withRequestIdAndUserId(requestId, userId);
        try {
            ListingApiResponse response = listingRestClient
                    .get()
                    .uri("/api/v1/listings/{id}", listingId)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .body(ListingApiResponse.class);
            if (response == null) {
                throw new ListingUnavailableException("Listing service returned empty body for id: " + listingId);
            }
            return new ListingSnapshot(
                    response.id(), response.species(), response.breed(), response.age(), response.passportId());
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ListingNotFoundException("Listing not found with id: " + listingId);
        } catch (HttpClientErrorException.Forbidden ex) {
            throw new ListingNotFoundException("Listing not found or not accessible: " + listingId);
        } catch (HttpClientErrorException ex) {
            log.warn(
                    "Listing service error for id={} status={} requestId={}",
                    listingId,
                    ex.getStatusCode().value(),
                    requestId);
            throw new ListingUnavailableException(
                    "Listing service error: " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            log.warn("Listing service unavailable for id={} requestId={}", listingId, requestId, ex);
            throw new ListingUnavailableException("Listing service unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    private ListingSnapshot getListingFallback(long listingId, long userId, String requestId, Throwable cause) {
        log.warn("Listing service circuit open or call failed for id={} requestId={}", listingId, requestId, cause);
        throw new ListingUnavailableException("Listing service unavailable", cause);
    }

    /**
     * Returns one page of PUBLISHED listings (catalog feed) along with the total
     * element count, so callers can iterate through all candidates for scoring.
     */
    @CircuitBreaker(name = "listingService", fallbackMethod = "getPublishedListingsFallback")
    public PublishedListingsPage getPublishedListings(int page, int size, String requestId) {
        HttpHeaders headers = ServiceClientHeaders.withRequestId(requestId);
        try {
            ListingPageResponse response = listingRestClient
                    .get()
                    .uri(builder -> builder.path("/api/v1/listings")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ListingPageResponse>() {});
            if (response == null) {
                throw new ListingUnavailableException("Listing service returned empty body for catalog page " + page);
            }
            return new PublishedListingsPage(
                    response.content() == null ? List.of() : response.content(),
                    response.totalElements(),
                    response.totalPages(),
                    response.number(),
                    response.size());
        } catch (HttpClientErrorException ex) {
            log.warn(
                    "Listing service error fetching page={} size={} status={} requestId={}",
                    page,
                    size,
                    ex.getStatusCode().value(),
                    requestId);
            throw new ListingUnavailableException(
                    "Listing service error: " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            log.warn("Listing service unavailable for catalog page={} requestId={}", page, requestId, ex);
            throw new ListingUnavailableException("Listing service unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    private PublishedListingsPage getPublishedListingsFallback(int page, int size, String requestId, Throwable cause) {
        log.warn(
                "Listing service circuit open or call failed for catalog page={} requestId={}", page, requestId, cause);
        throw new ListingUnavailableException("Listing service unavailable", cause);
    }

    private record ListingApiResponse(Long id, String species, String breed, Integer age, String passportId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ListingPageResponse(
            List<ListingSummary> content, long totalElements, int totalPages, int number, int size) {}

    public record PublishedListingsPage(
            List<ListingSummary> content, long totalElements, int totalPages, int number, int size) {}
}
