package ru.hvostid.passport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Returned from the protected ticket-issuance endpoint. The url is a
 * gateway-public, ticketed path that streams the document bytes via the
 * X-Accel-Redirect handoff in the frontend nginx.
 */
@Schema(description = "Short lived URL for streaming a passport document.")
public record MediaTicketResponse(
        @Schema(description = "Relative URL to stream the document content.")
        String url,

        @Schema(description = "Instant after which the URL stops working.")
        Instant expiresAt) {}
