package ru.hvostid.matching.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PassportServiceClient {
    private static final Logger log = LoggerFactory.getLogger(PassportServiceClient.class);

    private final RestClient passportRestClient;

    public PassportServiceClient(RestClient passportRestClient) {
        this.passportRestClient = passportRestClient;
    }

    @CircuitBreaker(name = "passportService", fallbackMethod = "getPassportFallback")
    public Optional<PassportSnapshot> getPassport(long passportId, String requestId) {
        HttpHeaders headers = ServiceClientHeaders.withRequestId(requestId);
        try {
            PassportApiResponse response = passportRestClient
                    .get()
                    .uri("/internal/passports/{id}", passportId)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .body(PassportApiResponse.class);
            if (response == null) {
                log.warn("Passport service returned empty body for id={} requestId={}", passportId, requestId);
                return Optional.empty();
            }
            return Optional.of(new PassportSnapshot(
                    response.species(), response.breed(), response.temperament(), response.specialNeeds()));
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Passport not found id={} requestId={}", passportId, requestId);
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Passport service unavailable for id={} requestId={}", passportId, requestId, ex);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unused")
    private Optional<PassportSnapshot> getPassportFallback(long passportId, String requestId, Throwable cause) {
        log.warn("Passport service circuit open or call failed for id={} requestId={}", passportId, requestId, cause);
        return Optional.empty();
    }

    private record PassportApiResponse(String species, String breed, String temperament, String specialNeeds) {}
}
