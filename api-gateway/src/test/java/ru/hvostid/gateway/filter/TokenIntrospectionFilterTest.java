package ru.hvostid.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.hvostid.common.contract.auth.IntrospectResponse;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.gateway.client.IntrospectionClient;
import ru.hvostid.gateway.config.AuthProperties;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;

@ExtendWith(MockitoExtension.class)
class TokenIntrospectionFilterTest {
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/actuator/**"
    );

    @Mock
    private IntrospectionClient introspectionClient;

    private TokenIntrospectionFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AuthProperties authProperties = new AuthProperties(
                "http://localhost:8081/internal/auth/introspect",
                Duration.ofSeconds(3),
                PUBLIC_PATHS
        );
        filter = new TokenIntrospectionFilter(introspectionClient, authProperties, objectMapper);
    }

    @Nested
    @DisplayName("Public paths")
    class PublicPathTests {
        @Test
        @DisplayName("login path should not be filtered")
        void loginPath_shouldNotFilter() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            assertTrue(filter.shouldNotFilter(request));
        }

        @Test
        @DisplayName("register path should not be filtered")
        void registerPath_shouldNotFilter() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
            assertTrue(filter.shouldNotFilter(request));
        }

        @Test
        @DisplayName("actuator paths should not be filtered")
        void actuatorPath_shouldNotFilter() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            assertTrue(filter.shouldNotFilter(request));
        }

        @Test
        @DisplayName("protected paths should be filtered")
        void protectedPath_shouldFilter() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
            assertFalse(filter.shouldNotFilter(request));
        }
    }

    @Nested
    @DisplayName("Missing or invalid Authorization header")
    class MissingAuthTests {
        @Test
        @DisplayName("no Authorization header returns 401")
        void noAuthHeader_returns401() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, mock(FilterChain.class));

            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
            verifyNoInteractions(introspectionClient);
        }

        @Test
        @DisplayName("non-Bearer Authorization header returns 401")
        void nonBearerHeader_returns401() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, mock(FilterChain.class));

            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
            verifyNoInteractions(introspectionClient);
        }

        @Test
        @DisplayName("401 response body contains error details")
        void unauthorizedResponse_containsErrorDetails() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, mock(FilterChain.class));

            String body = response.getContentAsString();
            assertTrue(body.contains("\"status\":401"));
            assertTrue(body.contains("\"path\":\"/api/v1/listings\""));
        }
    }

    @Nested
    @DisplayName("Introspection results")
    class IntrospectionTests {
        @Test
        @DisplayName("active token forwards request with user headers")
        void activeToken_forwardsWithUserHeaders() throws ServletException, IOException {
            IntrospectResponse activeResponse = new IntrospectResponse(true, 42L, List.of(UserRole.BUYER.value()));
            when(introspectionClient.introspect("valid-token")).thenReturn(Optional.of(activeResponse));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> capturedUserId = new AtomicReference<>();
            AtomicReference<String> capturedRoles = new AtomicReference<>();

            FilterChain chain = (req, _) -> {
                HttpServletRequest httpReq = (HttpServletRequest) req;
                capturedUserId.set(httpReq.getHeader(USER_ID));
                capturedRoles.set(httpReq.getHeader(USER_ROLES));
            };

            filter.doFilterInternal(request, response, chain);

            assertEquals("42", capturedUserId.get());
            assertEquals(UserRole.BUYER.value(), capturedRoles.get());
            assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        }

        @Test
        @DisplayName("active token with multiple roles joins them with comma")
        void activeToken_multipleRoles_joinedWithComma() throws ServletException, IOException {
            IntrospectResponse activeResponse = new IntrospectResponse(true, 1L, List.of(UserRole.SELLER.value(), UserRole.ADMIN.value()));
            when(introspectionClient.introspect("multi-role-token")).thenReturn(Optional.of(activeResponse));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer multi-role-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> capturedRoles = new AtomicReference<>();

            FilterChain chain = (req, _) ->
                    capturedRoles.set(((HttpServletRequest) req).getHeader(USER_ROLES));

            filter.doFilterInternal(request, response, chain);

            assertEquals("SELLER,ADMIN", capturedRoles.get());
        }

        @Test
        @DisplayName("inactive token returns 401")
        void inactiveToken_returns401() throws ServletException, IOException {
            IntrospectResponse inactiveResponse = new IntrospectResponse(false, null, null);
            when(introspectionClient.introspect("expired-token")).thenReturn(Optional.of(inactiveResponse));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, mock(FilterChain.class));

            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        }

        @Test
        @DisplayName("introspection error returns 401")
        void introspectionError_returns401() throws ServletException, IOException {
            when(introspectionClient.introspect(anyString())).thenReturn(Optional.empty());

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, mock(FilterChain.class));

            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        }
    }

    @Nested
    @DisplayName("UserInfoHeaderWrapper")
    class WrapperTests {
        @Test
        @DisplayName("getHeaderNames includes injected headers")
        void getHeaderNames_includesInjectedHeaders() throws ServletException, IOException {
            IntrospectResponse activeResponse = new IntrospectResponse(true, 10L, List.of(UserRole.BUYER.value()));
            when(introspectionClient.introspect("token")).thenReturn(Optional.of(activeResponse));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            AtomicReference<Boolean> hasUserId = new AtomicReference<>(false);
            AtomicReference<Boolean> hasRoles = new AtomicReference<>(false);

            FilterChain chain = (req, _) -> {
                HttpServletRequest httpReq = (HttpServletRequest) req;
                var names = java.util.Collections.list(httpReq.getHeaderNames());
                hasUserId.set(names.stream().anyMatch(USER_ID::equalsIgnoreCase));
                hasRoles.set(names.stream().anyMatch(USER_ROLES::equalsIgnoreCase));
            };

            filter.doFilterInternal(request, response, chain);

            assertTrue(hasUserId.get(), "Header names must include " + USER_ID);
            assertTrue(hasRoles.get(), "Header names must include " + USER_ROLES);
        }
    }
}
