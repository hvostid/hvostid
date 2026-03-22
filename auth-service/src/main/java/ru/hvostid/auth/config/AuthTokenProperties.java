package ru.hvostid.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for token lifetimes.
 * Bound from application.yml under "hvostid.auth".
 */
@ConfigurationProperties(prefix = "hvostid.auth")
public record AuthTokenProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
