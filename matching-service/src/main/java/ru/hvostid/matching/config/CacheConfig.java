package ru.hvostid.matching.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    public static final String MATCH_SCORES_CACHE = "matchScores";
}
