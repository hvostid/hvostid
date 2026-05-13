package ru.hvostid.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.hvostid.common.dto.ErrorResponse;
import ru.hvostid.common.http.SecurityHeaders;
import ru.hvostid.gateway.config.RateLimitProperties;
import tools.jackson.databind.ObjectMapper;

/**
 * Rate limiting filter using the token bucket algorithm.
 * <p>
 * Each client IP address gets its own bucket with a configurable
 * replenish rate and burst capacity. Requests that exceed the limit
 * receive a 429 Too Many Requests response.
 * <p>
 * Ordered before {@link TokenIntrospectionFilter} to reject excessive
 * requests early, before performing introspection calls.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        TokenBucket bucket = buckets.computeIfAbsent(
                clientIp, _ -> new TokenBucket(properties.replenishRate(), properties.burstCapacity()));

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for ip={} on {}", clientIp, request.getRequestURI());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "1");

            ErrorResponse error = new ErrorResponse(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "Rate limit exceeded. Try again later.",
                    request.getRequestURI(),
                    request.getHeader(SecurityHeaders.REQUEST_ID));

            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Take the first IP in the chain (original client)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Thread-safe token bucket implementation.
     * <p>
     * Tokens are replenished at {@code rate} tokens per second,
     * up to a maximum of {@code capacity} tokens. Each request
     * consumes one token.
     */
    static class TokenBucket {
        private final int rate;
        private final int capacity;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int rate, int capacity) {
            this.rate = rate;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
            double tokensToAdd = elapsed * rate;
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillNanos = now;
        }
    }
}
