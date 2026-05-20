package ru.hvostid.matching.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
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
        } catch (HttpClientErrorException.NotFound _) {
            throw new ListingNotFoundException("Listing not found with id: " + listingId);
        } catch (HttpClientErrorException.Forbidden _) {
            throw new ListingNotFoundException("Listing not found or not accessible: " + listingId);
        } catch (HttpClientErrorException ex) {
            throw new ListingUnavailableException(
                    "Listing service error " + ex.getStatusCode().value() + " for listingId=" + listingId
                            + " requestId=" + requestId,
                    ex);
        } catch (RestClientException ex) {
            throw new ListingUnavailableException(
                    "Listing service unavailable for listingId=" + listingId + " requestId=" + requestId, ex);
        }
    }

    @SuppressWarnings("unused")
    private ListingSnapshot getListingFallback(long listingId, long userId, String requestId, Throwable cause) {
        // ignore-exceptions keeps domain not-found out of the CB window, but
        // resilience4j still routes them through the fallback. Re-throw so the
        // controller maps to 404 instead of masking the upstream signal as 503.
        if (cause instanceof ListingNotFoundException notFound) {
            throw notFound;
        }
        throw new ListingUnavailableException(
                "Listing service unavailable for listingId=" + listingId + " requestId=" + requestId, cause);
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
                    .body(new ParameterizedTypeReference<>() {});
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
            throw new ListingUnavailableException(
                    "Listing service error " + ex.getStatusCode().value() + " for catalog page=" + page + " size="
                            + size + " requestId=" + requestId,
                    ex);
        } catch (RestClientException ex) {
            throw new ListingUnavailableException(
                    "Listing service unavailable for catalog page=" + page + " requestId=" + requestId, ex);
        }
    }

    @SuppressWarnings("unused")
    private PublishedListingsPage getPublishedListingsFallback(int page, int size, String requestId, Throwable cause) {
        throw new ListingUnavailableException(
                "Listing service unavailable for catalog page=" + page + " requestId=" + requestId, cause);
    }

    private record ListingApiResponse(Long id, String species, String breed, Integer age, String passportId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ListingPageResponse(
            List<ListingSummary> content, long totalElements, int totalPages, int number, int size) {}

    public record PublishedListingsPage(
            List<ListingSummary> content, long totalElements, int totalPages, int number, int size) {}
}
