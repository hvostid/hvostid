package ru.hvostid.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the rate limiter.
 * <p>
 * Bound to the "hvostid.rate-limit" prefix in application.yml.
 * Uses a token bucket algorithm: tokens are replenished at {@code replenishRate}
 * per second up to a maximum of {@code burstCapacity}.
 */
@ConfigurationProperties(prefix = "hvostid.rate-limit")
public record RateLimitProperties(int replenishRate, int burstCapacity) {
    public RateLimitProperties {
        if (replenishRate <= 0) {
            replenishRate = 20;
        }
        if (burstCapacity <= 0) {
            burstCapacity = 40;
        }
    }
}
