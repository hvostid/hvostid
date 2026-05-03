package ru.hvostid.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for token introspection and public paths.
 * <p>
 * Bound to the "hvostid.auth" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "hvostid.auth")
public record AuthProperties(String introspectUrl, Duration introspectTimeout, List<String> publicPaths) {
    public AuthProperties {
        if (introspectTimeout == null) {
            introspectTimeout = Duration.ofSeconds(3);
        }
        if (publicPaths == null) {
            publicPaths = List.of();
        }
    }
}
