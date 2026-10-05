package ru.hvostid.listing.client;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.hvostid.common.exception.ConflictException;
import ru.hvostid.common.exception.ValidationException;

@Component
public class PassportServiceClient {
    private final RestClient client;

    public PassportServiceClient(RestClient passportRestClient) {
        this.client = passportRestClient;
    }

    public void validateOwner(long passportId, long sellerId) {
        call(() -> {
            var p = client.get()
                    .uri("/internal/passports/{id}", passportId)
                    .retrieve()
                    .body(Owner.class);
            if (p == null || p.sellerId() != sellerId)
                throw new ValidationException("Passport does not belong to this seller");
        });
    }

    public void acquire(long passportId, long listingId, long sellerId, boolean approved, long revision) {
        call(() -> client.put()
                .uri("/internal/passports/{id}/references/{listing}", passportId, listingId)
                .body(Map.of("sellerId", sellerId, "approved", approved, "revision", revision))
                .retrieve()
                .toBodilessEntity());
    }

    public void release(long passportId, long listingId, long revision) {
        call(() -> client.delete()
                .uri(
                        "/internal/passports/{id}/references/{listing}?revision={revision}",
                        passportId,
                        listingId,
                        revision)
                .retrieve()
                .toBodilessEntity());
    }

    private void call(Runnable operation) {
        try {
            operation.run();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError())
                throw new ConflictException(
                        "Passport is unavailable, not owned by the seller, or already referenced by another active listing");
            throw new PassportServiceUnavailableException(ex);
        } catch (RestClientException ex) {
            throw new PassportServiceUnavailableException(ex);
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(long sellerId) {}

    public static class PassportServiceUnavailableException extends RuntimeException {
        public PassportServiceUnavailableException(Throwable cause) {
            super("Passport service unavailable; retry later", cause);
        }
    }
}
