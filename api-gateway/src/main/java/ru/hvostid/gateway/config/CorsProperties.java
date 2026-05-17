package ru.hvostid.gateway.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS settings for production deployments.
 * <p>
 * Bound to the "hvostid.cors" prefix. {@code allowedOrigins} accepts a comma-separated
 * list via the {@code HVOSTID_CORS_ALLOWED_ORIGINS} environment variable.
 */
@ConfigurationProperties(prefix = "hvostid.cors")
public record CorsProperties(String allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            allowedOrigins = "http://localhost";
        }
    }

    public List<String> allowedOriginList() {
        return parseOrigins(allowedOrigins);
    }

    /**
     * Parses a comma-separated origins string (as used in {@code HVOSTID_CORS_ALLOWED_ORIGINS}).
     */
    public static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("http://localhost");
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
