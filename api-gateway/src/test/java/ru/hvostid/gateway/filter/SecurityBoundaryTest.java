package ru.hvostid.gateway.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.hvostid.common.http.SecurityHeaders.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.hvostid.gateway.client.IntrospectionClient;
import ru.hvostid.gateway.client.IntrospectionUnavailableException;
import ru.hvostid.gateway.config.AuthProperties;
import ru.hvostid.gateway.config.RateLimitProperties;
import tools.jackson.databind.ObjectMapper;

class SecurityBoundaryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void anonymousIdentityIsStrippedBeforePublicAndOptionalPaths() throws Exception {
        IntrospectionClient client = mock(IntrospectionClient.class);
        var authentication = new TokenIntrospectionFilter(
                client,
                new AuthProperties(
                        "http://auth",
                        Duration.ofSeconds(1),
                        List.of("GET /api/v1/listings"),
                        List.of("GET /api/v1/listings/{id:[0-9]+}")),
                mapper);
        for (String path : List.of("/api/v1/listings", "/api/v1/listings/1")) {
            var request = new MockHttpServletRequest("GET", path);
            request.addHeader(USER_ID, "42");
            request.addHeader(USER_ROLES, "ADMIN,SELLER");
            new IdentityHeaderFilter()
                    .doFilter(
                            request,
                            new MockHttpServletResponse(),
                            (clean, response) -> authentication.doFilter(clean, response, (forwarded, ignored) -> {
                                var actual = (HttpServletRequest) forwarded;
                                assertNull(actual.getHeader(USER_ID));
                                assertNull(actual.getHeader(USER_ROLES));
                                assertFalse(actual.getHeaders(USER_ID).hasMoreElements());
                                assertFalse(Collections.list(actual.getHeaderNames())
                                        .contains(USER_ROLES));
                            }));
        }
        verifyNoInteractions(client);
        for (String path : List.of("/api/v1/listings/my", "/api/v1/listings/draft")) {
            var request = new MockHttpServletRequest("GET", path);
            request.addHeader(USER_ID, "42");
            var response = new MockHttpServletResponse();
            new IdentityHeaderFilter()
                    .doFilter(
                            request,
                            response,
                            (clean, output) -> authentication.doFilter(
                                    clean, output, (ignored, ignored2) -> fail("Private route passed")));
            assertEquals(401, response.getStatus());
        }
    }

    @Test
    void unavailableIntrospectionDoesNotInvalidateCredentials() throws Exception {
        IntrospectionClient client = mock(IntrospectionClient.class);
        when(client.introspect("valid")).thenThrow(new IntrospectionUnavailableException(null));
        var filter = new TokenIntrospectionFilter(
                client, new AuthProperties("http://auth", null, List.of(), List.of()), mapper);
        var request = new MockHttpServletRequest("GET", "/api/v1/profile/me");
        request.addHeader("Authorization", "Bearer valid");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignored, ignored2) -> fail("Unavailable auth passed"));
        assertEquals(503, response.getStatus());
    }

    @Test
    void changingUntrustedForwardedHeadersDoesNotCreateNewBuckets() throws Exception {
        var filter = new RateLimitFilter(new RateLimitProperties(1, 1), mapper);
        for (int i = 0; i < 3; i++) {
            var request = new MockHttpServletRequest("GET", "/api/v1/listings");
            request.setRemoteAddr("198.51.100.9");
            request.addHeader("X-Forwarded-For", "203.0.113." + i);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (ignored, ignored2) -> {});
            assertEquals(i == 0 ? 200 : 429, response.getStatus());
        }
        assertEquals(1, filter.trackedClients());
    }

    @Test
    void trustedProxyUsesRightmostUntrustedAddressAndCacheIsBounded() throws Exception {
        var properties = new RateLimitProperties(1, 1, 2, Duration.ofMinutes(15), List.of("127.0.0.1"), 1, 1);
        var filter = new RateLimitFilter(properties, mapper);
        for (int i = 0; i < 4; i++) {
            var request = new MockHttpServletRequest("GET", "/api/v1/listings");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100." + i);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (ignored, ignored2) -> {});
            assertEquals(i < 2 ? 200 : 429, response.getStatus());
        }
        assertEquals(2, filter.trackedClients());
    }
}
