package ru.hvostid.gateway.client;

import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.hvostid.common.contract.auth.IntrospectResponse;

/**
 * Client that calls Auth Service token introspection endpoint.
 * <p>
 * Distinguishes inactive credentials from an unavailable authentication service.
 */
@Component
public class IntrospectionClient {
    private static final Logger log = LoggerFactory.getLogger(IntrospectionClient.class);

    private final RestClient restClient;

    public IntrospectionClient(RestClient introspectionRestClient) {
        this.restClient = introspectionRestClient;
    }

    /**
     * Call the introspection endpoint with the given token.
     *
     * @param token opaque access token to validate
     * @return active or inactive introspection result
     * @throws IntrospectionUnavailableException when the service cannot provide a valid response
     */
    public Optional<IntrospectResponse> introspect(String token) {
        try {
            IntrospectResponse response = restClient
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", token))
                    .retrieve()
                    .body(IntrospectResponse.class);

            if (response == null) {
                throw new IntrospectionUnavailableException(null);
            }
            return Optional.of(response);
        } catch (RestClientException ex) {
            log.warn("Introspection call failed: {}", ex.getMessage());
            throw new IntrospectionUnavailableException(ex);
        }
    }
}
