package ru.hvostid.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Error body in RFC 7807 (Problem Details for HTTP APIs) format with one project-specific
 * extension: {@code traceId} carrying the {@code X-Request-Id} correlation value.
 *
 * <p>Serialized as {@code application/problem+json}. Nulls are suppressed so the body stays
 * compact when optional fields are not set.
 */
@Schema(description = "RFC 7807 problem detail body returned by every service for non-2xx responses")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetails(
        @Schema(description = "URI identifying the problem type", example = "urn:problem-type:not-found")
        String type,

        @Schema(description = "Short, human-readable summary of the problem type", example = "Listing not found")
        String title,

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(
                description = "Human-readable explanation specific to this occurrence",
                example = "Listing with id 123 does not exist")
        String detail,

        @Schema(description = "URI reference identifying the specific occurrence", example = "/api/v1/listings/123")
        String instance,

        @Schema(
                description = "Correlation id propagated from X-Request-Id",
                example = "5f0c2a3a-4b1c-4f0a-9b7e-c2a3d1b0f8e1")
        String traceId,

        @Schema(description = "Per-field validation errors (only present on 400 validation responses)")
        List<FieldViolation> errors,

        @Schema(description = "Server timestamp when the problem was produced", example = "2026-05-18T10:15:30Z")
        Instant timestamp) {

    @Schema(description = "One field-level validation error")
    public record FieldViolation(
            @Schema(description = "Failing field name", example = "title")
            String field,

            @Schema(description = "Reason the field is invalid", example = "Title must be between 3 and 255 characters")
            String message) {}
}
