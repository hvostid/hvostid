package ru.hvostid.passport.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.hvostid.common.http.SecurityHeaders;
import ru.hvostid.passport.exception.ListingServiceUnavailableException;

@Component
public class ListingServiceClient {
    private static final Logger log = LoggerFactory.getLogger(ListingServiceClient.class);

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
            log.warn("Listing service unavailable while checking passportId={}", passportId, ex);
            throw new ListingServiceUnavailableException("Listing service unavailable", ex);
        }
    }

    private record HasPublishedResponse(String passportId, boolean hasPublishedListing) {}
}
