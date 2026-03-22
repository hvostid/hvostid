package ru.hvostid.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GatewayRouteConfigTest {
    @Autowired
    private Environment environment;

    @Test
    void shouldDefineExactlyFourRoutes() {
        for (int i = 0; i < 4; i++) {
            String id = environment.getProperty(routeKey(i, "id"));
            assertNotNull(id, "Route at index " + i + " should be defined");
        }
        assertNull(environment.getProperty(routeKey(4, "id")),
                "There should be no fifth route");
    }

    @ParameterizedTest
    @CsvSource({
            "0, auth-service,     http://localhost:8081",
            "1, listing-service,  http://localhost:8082",
            "2, passport-service, http://localhost:8083",
            "3, matching-service, http://localhost:8084"
    })
    void shouldConfigureRouteIdAndUri(int index, String expectedId, String expectedUri) {
        assertEquals(expectedId, environment.getProperty(routeKey(index, "id")));
        assertEquals(expectedUri, environment.getProperty(routeKey(index, "uri")));
    }

    @Test
    void authRoutePredicateShouldMatchAuthAndProfilePaths() {
        // Single Path predicate with two comma-separated patterns
        String predicate = environment.getProperty(predicateKey(0, 0));
        assertNotNull(predicate, "Auth route must have a path predicate");
        assertTrue(predicate.contains("/api/v1/auth/**"),
                "Auth route should match /api/v1/auth/**");
        assertTrue(predicate.contains("/api/v1/profile/**"),
                "Auth route should match /api/v1/profile/**");
    }

    @ParameterizedTest
    @CsvSource({
            "1, /api/v1/listings/**",
            "2, /api/v1/passports/**",
            "3, /api/v1/match/**"
    })
    void shouldConfigurePathPredicateForRoute(int index, String expectedPath) {
        String predicate = environment.getProperty(predicateKey(index, 0));
        assertNotNull(predicate, "Route at index " + index + " should have a path predicate");
        assertTrue(predicate.contains(expectedPath),
                "Route predicate should contain " + expectedPath);
    }

    @Test
    void serviceUriDefaultsPointToLocalhost() {
        // In production, env vars AUTH_SERVICE_HOST, LISTING_SERVICE_HOST, etc.
        // override the host portion. Defaults use localhost.
        assertEquals("http://localhost:8081", environment.getProperty(routeKey(0, "uri")));
        assertEquals("http://localhost:8082", environment.getProperty(routeKey(1, "uri")));
        assertEquals("http://localhost:8083", environment.getProperty(routeKey(2, "uri")));
        assertEquals("http://localhost:8084", environment.getProperty(routeKey(3, "uri")));
    }

    private String routeKey(int index, String property) {
        return "spring.cloud.gateway.routes[" + index + "]." + property;
    }

    private String predicateKey(int routeIndex, int predicateIndex) {
        return "spring.cloud.gateway.routes[" + routeIndex + "].predicates[" + predicateIndex + "]";
    }
}
