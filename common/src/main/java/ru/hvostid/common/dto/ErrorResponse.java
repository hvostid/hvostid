package ru.hvostid.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * Standard error response returned by all services.
 *
 * <p>Today the body follows a flat {status, error, message, path, timestamp, requestId, fieldErrors}
 * shape. Migration to RFC 7807 ProblemDetails is tracked in T38.
 */
@Schema(description = "Standard error body returned by every service for non-2xx responses")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "HTTP reason phrase", example = "Not Found")
        String error,

        @Schema(description = "Human-readable failure detail", example = "Listing 1 not found")
        String message,

        @Schema(description = "Request path that produced the error", example = "/api/v1/listings/1")
        String path,

        @Schema(description = "Server timestamp when the error was produced", example = "2026-05-13T10:15:30Z")
        Instant timestamp,

        @Schema(
                description = "Correlation identifier propagated from X-Request-Id (optional)",
                example = "5f0c2a3a-4b1c-4f0a-9b7e-c2a3d1b0f8e1")
        String requestId,

        @Schema(
                description = "Per-field validation messages keyed by request-body field name (optional, "
                        + "present only on 400 validation responses)",
                example = "{\"price\":\"must be greater than or equal to 0\"}")
        Map<String, String> fieldErrors) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, Instant.now(), null, null);
    }

    public ErrorResponse(int status, String error, String message, String path, String requestId) {
        this(status, error, message, path, Instant.now(), requestId, null);
    }

    public ErrorResponse(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        this(status, error, message, path, Instant.now(), null, fieldErrors);
    }

    public ErrorResponse(
            int status, String error, String message, String path, String requestId, Map<String, String> fieldErrors) {
        this(status, error, message, path, Instant.now(), requestId, fieldErrors);
    }
}
