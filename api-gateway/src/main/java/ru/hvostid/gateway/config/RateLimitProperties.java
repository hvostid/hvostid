package ru.hvostid.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hvostid.rate-limit")
public record RateLimitProperties(
        int replenishRate,
        int burstCapacity,
        int maxClients,
        Duration idleTtl,
        List<String> trustedProxies,
        int authReplenishRate,
        int authBurstCapacity) {
    public RateLimitProperties(int replenishRate, int burstCapacity) {
        this(replenishRate, burstCapacity, 10000, Duration.ofMinutes(15), List.of(), 1, 10);
    }

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public RateLimitProperties {
        if (replenishRate <= 0) replenishRate = 20;
        if (burstCapacity <= 0) burstCapacity = 40;
        if (maxClients <= 0) maxClients = 10000;
        if (authReplenishRate <= 0) authReplenishRate = 1;
        if (authBurstCapacity <= 0) authBurstCapacity = 10;
        Duration refillTime = Duration.ofSeconds(Math.max(
                (burstCapacity + replenishRate - 1) / replenishRate,
                (authBurstCapacity + authReplenishRate - 1) / authReplenishRate));
        if (idleTtl == null) idleTtl = Duration.ofMinutes(15);
        if (idleTtl.compareTo(refillTime) < 0) idleTtl = refillTime;
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}
