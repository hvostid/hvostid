package ru.hvostid.common.openapi;

/**
 * Shared OpenAPI security-scheme identifiers used by every service that
 * is fronted by the API gateway and authenticated via injected request
 * headers.
 */
public final class OpenApiSecuritySchemes {
    /**
     * Identifier of the API-key scheme that maps to {@code SecurityHeaders.USER_ID}
     * (i.e. {@code X-User-Id}). Used by every internal service that consumes
     * gateway pre-authenticated requests.
     */
    public static final String USER_ID_SCHEME = "userIdHeader";

    private OpenApiSecuritySchemes() {
    }
}
