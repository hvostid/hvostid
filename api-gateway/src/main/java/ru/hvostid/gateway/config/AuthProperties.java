package ru.hvostid.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration properties for token introspection and public paths.
 * <p>
 * Bound to the "hvostid.auth" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "hvostid.auth")
public record AuthProperties(
        String introspectUrl,
        Duration introspectTimeout,
        List<String> publicPaths
) {
    public AuthProperties {
        if (introspectTimeout == null) {
            introspectTimeout = Duration.ofSeconds(3);
        }
        if (publicPaths == null) {
            publicPaths = List.of();
        }
    }
}
