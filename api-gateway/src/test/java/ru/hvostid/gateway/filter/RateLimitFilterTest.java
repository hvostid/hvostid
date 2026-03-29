package ru.hvostid.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.hvostid.gateway.config.RateLimitProperties;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RateLimitFilterTest {
    private RateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Token bucket behavior")
    class TokenBucketTests {
        @Test
        @DisplayName("requests within burst capacity are allowed")
        void withinBurst_allowed() throws ServletException, IOException {
            // Small bucket for testing: 5 req/sec, burst 10
            filter = new RateLimitFilter(new RateLimitProperties(5, 10), objectMapper);
            AtomicInteger passedCount = new AtomicInteger(0);
            FilterChain chain = (_, _) -> passedCount.incrementAndGet();

            for (int i = 0; i < 10; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
                request.setRemoteAddr("10.0.0.1");
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilterInternal(request, response, chain);
            }

            assertEquals(10, passedCount.get(), "All requests within burst capacity should pass");
        }

        @Test
        @DisplayName("requests exceeding burst capacity return 429")
        void exceedingBurst_returns429() throws ServletException, IOException {
            // Tiny bucket: 1 req/sec, burst 2
            filter = new RateLimitFilter(new RateLimitProperties(1, 2), objectMapper);

            int rejectedCount = 0;

            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
                request.setRemoteAddr("10.0.0.2");
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilterInternal(request, response, mock(FilterChain.class));

                if (response.getStatus() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    rejectedCount++;
                }
            }

            assertTrue(rejectedCount > 0, "Some requests should be rejected when exceeding burst capacity");
        }

        @Test
        @DisplayName("429 response includes Retry-After header")
        void tooManyRequests_hasRetryAfterHeader() throws ServletException, IOException {
            filter = new RateLimitFilter(new RateLimitProperties(1, 1), objectMapper);

            // Exhaust the single token
            MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/v1/listings");
            request1.setRemoteAddr("10.0.0.3");
            filter.doFilterInternal(request1, new MockHttpServletResponse(), mock(FilterChain.class));

            // Second request should be rejected
            MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/listings");
            request2.setRemoteAddr("10.0.0.3");
            MockHttpServletResponse response2 = new MockHttpServletResponse();
            filter.doFilterInternal(request2, response2, mock(FilterChain.class));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response2.getStatus());
            assertNotNull(response2.getHeader("Retry-After"));
        }

        @Test
        @DisplayName("429 response body contains error details")
        void tooManyRequests_containsErrorBody() throws ServletException, IOException {
            filter = new RateLimitFilter(new RateLimitProperties(1, 1), objectMapper);

            // Exhaust the token
            MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/v1/listings");
            request1.setRemoteAddr("10.0.0.4");
            filter.doFilterInternal(request1, new MockHttpServletResponse(), mock(FilterChain.class));

            // Trigger 429
            MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/listings");
            request2.setRemoteAddr("10.0.0.4");
            MockHttpServletResponse response2 = new MockHttpServletResponse();
            filter.doFilterInternal(request2, response2, mock(FilterChain.class));

            String body = response2.getContentAsString();
            assertTrue(body.contains("\"status\":429"));
            assertTrue(body.contains("Rate limit exceeded"));
        }
    }

    @Nested
    @DisplayName("Per-IP isolation")
    class PerIpTests {
        @Test
        @DisplayName("different IPs have separate buckets")
        void differentIps_separateBuckets() throws ServletException, IOException {
            filter = new RateLimitFilter(new RateLimitProperties(1, 1), objectMapper);

            // Exhaust bucket for IP-A
            MockHttpServletRequest requestA = new MockHttpServletRequest("GET", "/api/v1/listings");
            requestA.setRemoteAddr("10.0.0.10");
            MockHttpServletResponse responseA = new MockHttpServletResponse();
            filter.doFilterInternal(requestA, responseA, mock(FilterChain.class));

            // IP-B should still be allowed
            MockHttpServletRequest requestB = new MockHttpServletRequest("GET", "/api/v1/listings");
            requestB.setRemoteAddr("10.0.0.11");
            MockHttpServletResponse responseB = new MockHttpServletResponse();
            AtomicInteger passed = new AtomicInteger(0);
            filter.doFilterInternal(requestB, responseB, (_, _) -> passed.incrementAndGet());

            assertEquals(1, passed.get(), "Different IP should not be affected by another IP's rate limit");
        }

        @Test
        @DisplayName("X-Forwarded-For header is used to resolve client IP")
        void xForwardedFor_usedForIpResolution() throws ServletException, IOException {
            filter = new RateLimitFilter(new RateLimitProperties(1, 1), objectMapper);

            // Exhaust bucket for the forwarded IP
            MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/v1/listings");
            request1.setRemoteAddr("127.0.0.1");
            request1.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
            filter.doFilterInternal(request1, new MockHttpServletResponse(), mock(FilterChain.class));

            // Same forwarded IP should be rate limited
            MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/listings");
            request2.setRemoteAddr("127.0.0.1");
            request2.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
            MockHttpServletResponse response2 = new MockHttpServletResponse();
            filter.doFilterInternal(request2, response2, mock(FilterChain.class));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response2.getStatus());
        }
    }

    @Nested
    @DisplayName("Token bucket refill")
    class RefillTests {
        @Test
        @DisplayName("tokens refill after waiting")
        void tokensRefill_afterWaiting() throws Exception {
            // Rate: 100 req/sec so refill is fast for testing
            filter = new RateLimitFilter(new RateLimitProperties(100, 1), objectMapper);

            // Exhaust the single token
            MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/v1/listings");
            request1.setRemoteAddr("10.0.0.20");
            filter.doFilterInternal(request1, new MockHttpServletResponse(), mock(FilterChain.class));

            // Wait enough for at least 1 token to refill (100/sec = 10ms per token)
            Thread.sleep(50);

            // Should be allowed again
            MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/listings");
            request2.setRemoteAddr("10.0.0.20");
            MockHttpServletResponse response2 = new MockHttpServletResponse();
            AtomicInteger passed = new AtomicInteger(0);
            filter.doFilterInternal(request2, response2, (_, _) -> passed.incrementAndGet());

            assertEquals(1, passed.get(), "Request should be allowed after tokens refill");
        }
    }
}
