package ru.hvostid.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
    private final Map<String, TokenBucket> buckets = new HashMap<>();
    private long nextCleanupNanos;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        boolean authentication = request.getRequestURI().startsWith("/api/v1/auth/");
        TokenBucket bucket = bucketFor(clientIp + (authentication ? ":auth" : ":api"), authentication);

        if (bucket == null || !bucket.tryConsume()) {
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
        String peer = request.getRemoteAddr();
        if (forwarded == null
                || forwarded.length() > 1024
                || !properties.trustedProxies().contains(peer)) {
            return peer;
        }
        String[] chain = forwarded.split(",");
        for (int i = chain.length - 1; i >= 0; i--) {
            String address = chain[i].trim();
            if (address.isEmpty() || address.length() > 45 || !address.matches("[0-9a-fA-F:.]+")) return peer;
            if (!properties.trustedProxies().contains(address)) return address;
        }
        return peer;
    }

    private synchronized TokenBucket bucketFor(String key, boolean authentication) {
        long now = System.nanoTime();
        if (now >= nextCleanupNanos || buckets.size() >= properties.maxClients()) {
            long cutoff = now - properties.idleTtl().toNanos();
            buckets.values().removeIf(bucket -> bucket.lastAccessNanos < cutoff);
            nextCleanupNanos = now + java.time.Duration.ofMinutes(1).toNanos();
        }
        TokenBucket existing = buckets.get(key);
        if (existing != null) {
            existing.lastAccessNanos = now;
            return existing;
        }
        // Do not evict active buckets: rotating identities must not replenish their quota.
        if (buckets.size() >= properties.maxClients()) return null;
        TokenBucket created = new TokenBucket(
                authentication ? properties.authReplenishRate() : properties.replenishRate(),
                authentication ? properties.authBurstCapacity() : properties.burstCapacity());
        buckets.put(key, created);
        return created;
    }

    synchronized int trackedClients() {
        return buckets.size();
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
        private volatile long lastAccessNanos = System.nanoTime();

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
