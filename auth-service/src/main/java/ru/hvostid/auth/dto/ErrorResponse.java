package ru.hvostid.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Standard error response body.
 */
@Schema(description = "Error response body returned for non-2xx responses")
public record ErrorResponse(
        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "HTTP reason phrase", example = "Bad Request")
        String error,

        @Schema(description = "Human-readable failure detail", example = "Email is required")
        String message,

        @Schema(description = "Server timestamp at which the error was produced", example = "2026-05-13T10:15:30Z")
        Instant timestamp) {
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, Instant.now());
    }
}
