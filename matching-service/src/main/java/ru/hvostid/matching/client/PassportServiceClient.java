package ru.hvostid.matching.client;

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
                log.warn("Passport service returned empty body for id={}", passportId);
                return Optional.empty();
            }
            return Optional.of(new PassportSnapshot(
                    response.species(), response.breed(), response.temperament(), response.specialNeeds()));
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Passport not found id={}", passportId);
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Passport service unavailable for id={}", passportId, ex);
            return Optional.empty();
        }
    }

    private record PassportApiResponse(String species, String breed, String temperament, String specialNeeds) {}
}
