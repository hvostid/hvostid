package ru.hvostid.passport.client;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.hvostid.common.http.SecurityHeaders;
import ru.hvostid.passport.exception.ListingServiceUnavailableException;

@Component
public class ListingServiceClient {
    private final RestClient listingRestClient;

    public ListingServiceClient(RestClient listingRestClient) {
        this.listingRestClient = listingRestClient;
    }

    public boolean hasPublishedListingForPassport(Long passportId, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        if (requestId != null && !requestId.isBlank()) {
            headers.set(SecurityHeaders.REQUEST_ID, requestId);
        }
        try {
            HasPublishedResponse response = listingRestClient
                    .get()
                    .uri("/api/v1/listings/passports/{id}/has-published", passportId)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .body(HasPublishedResponse.class);
            return response != null && response.hasPublishedListing();
        } catch (RestClientException ex) {
            throw new ListingServiceUnavailableException(
                    "Listing service unavailable while checking passportId=" + passportId, ex);
        }
    }

    public boolean hasActiveListingForPassport(Long passportId, String requestId) {
        try {
            HasActiveResponse response = listingRestClient
                    .get()
                    .uri("/internal/listings/passports/{id}/has-active", passportId)
                    .headers(headers -> {
                        if (requestId != null && !requestId.isBlank()) {
                            headers.set(SecurityHeaders.REQUEST_ID, requestId);
                        }
                    })
                    .retrieve()
                    .body(HasActiveResponse.class);
            // A missing answer must never authorize a destructive operation.
            if (response == null || response.hasActiveListing() == null) {
                throw new ListingServiceUnavailableException("Listing service returned no passport usage status");
            }
            return response.hasActiveListing();
        } catch (RestClientException ex) {
            throw new ListingServiceUnavailableException(
                    "Listing service unavailable while checking passportId=" + passportId, ex);
        }
    }

    private record HasActiveResponse(Boolean hasActiveListing) {}

    private record HasPublishedResponse(String passportId, boolean hasPublishedListing) {}
}
