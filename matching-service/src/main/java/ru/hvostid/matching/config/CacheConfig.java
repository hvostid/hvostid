package ru.hvostid.matching.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    public static final String MATCH_SCORES_CACHE = "matchScores";
    public static final String RECOMMENDATIONS_CACHE = "recommendations";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache(MATCH_SCORES_CACHE, Duration.ofMinutes(10), 10_000),
                buildCache(RECOMMENDATIONS_CACHE, Duration.ofMinutes(10), 1_000)));
        return manager;
    }

    private static CaffeineCache buildCache(String name, Duration ttl, long maxSize) {
        return new CaffeineCache(
                name,
                Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build());
    }
}
