package ru.hvostid.passport.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the media ticket flow.
 *
 * <p>ticketTtl bounds how long a freshly issued ticket can sit before a client
 * uses it. Keep this short (single digit minutes) since the client should
 * fetch a ticket only when it is about to load the image.
 *
 * <p>presignTtl bounds the MinIO signature lifetime baked into the
 * X-Accel-Redirect target. The frontend nginx consumes the redirect
 * immediately, so a tight value (seconds, at most a minute) limits replay
 * exposure if logs leak the redirect target.
 *
 * <p>internalRedirectPrefix is the nginx location declared as "internal" that
 * proxies to MinIO. The controller prepends it to the presigned path before
 * setting X-Accel-Redirect.
 */
@Validated
@ConfigurationProperties(prefix = "hvostid.media")
public record MediaTicketProperties(
        @NotNull Duration ticketTtl,
        @NotNull Duration presignTtl,
        @NotBlank String internalRedirectPrefix) {}
