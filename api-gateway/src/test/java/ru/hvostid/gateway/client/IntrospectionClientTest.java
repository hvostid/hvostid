package ru.hvostid.gateway.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.hvostid.common.contract.auth.IntrospectResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IntrospectionClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer mockServer;
    private IntrospectionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8081/internal/auth/introspect");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new IntrospectionClient(builder.build());
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    @DisplayName("active token returns present result with user data")
    void activeToken_returnsPresent() {
        String responseBody = objectMapper.writeValueAsString(
                new IntrospectResponse(true, 42L, java.util.List.of("buyer"))
        );

        mockServer.expect(requestTo("http://localhost:8081/internal/auth/introspect"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        Optional<IntrospectResponse> result = client.introspect("valid-token");

        assertTrue(result.isPresent());
        assertTrue(result.get().active());
        assertEquals(42L, result.get().userId());
        assertEquals(java.util.List.of("buyer"), result.get().roles());
    }

    @Test
    @DisplayName("inactive token returns present result with active=false")
    void inactiveToken_returnsInactive() {
        String responseBody = "{\"active\":false}";

        mockServer.expect(requestTo("http://localhost:8081/internal/auth/introspect"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        Optional<IntrospectResponse> result = client.introspect("expired-token");

        assertTrue(result.isPresent());
        assertFalse(result.get().active());
    }

    @Test
    @DisplayName("server error returns empty optional")
    void serverError_returnsEmpty() {
        mockServer.expect(requestTo("http://localhost:8081/internal/auth/introspect"))
                .andRespond(withServerError());

        Optional<IntrospectResponse> result = client.introspect("some-token");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("4xx error returns empty optional")
    void clientError_returnsEmpty() {
        mockServer.expect(requestTo("http://localhost:8081/internal/auth/introspect"))
                .andRespond(withBadRequest());

        Optional<IntrospectResponse> result = client.introspect("bad-token");

        assertTrue(result.isEmpty());
    }
}
