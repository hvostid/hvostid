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
    private static final String DEFAULT_ORIGIN = "http://localhost";

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            allowedOrigins = DEFAULT_ORIGIN;
        }
    }

    public List<String> allowedOriginList() {
        return parseOrigins(allowedOrigins);
    }

    /**
     * Parses a comma-separated origins string (as used in {@code HVOSTID_CORS_ALLOWED_ORIGINS}).
     * Falls back to {@code http://localhost} when the input is blank or contains no non-empty
     * entries (for example {@code ", ,"}); an empty list would otherwise disable CORS entirely.
     */
    public static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(DEFAULT_ORIGIN);
        }
        List<String> origins = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return origins.isEmpty() ? List.of(DEFAULT_ORIGIN) : origins;
    }
}
