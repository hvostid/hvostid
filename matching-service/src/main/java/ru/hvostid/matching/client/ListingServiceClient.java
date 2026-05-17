package ru.hvostid.matching.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        } catch (HttpClientErrorException ex) {
            log.warn(
                    "Listing service error for id={} status={}",
                    listingId,
                    ex.getStatusCode().value());
            throw new ListingUnavailableException(
                    "Listing service error: " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("Listing service unavailable for id={}", listingId, ex);
            throw new ListingUnavailableException("Listing service unavailable");
        }
    }

    private record ListingApiResponse(Long id, String species, String breed, Integer age, String passportId) {}
}
