package ru.hvostid.common.openapi;

/**
 * Shared OpenAPI security-scheme identifiers used by every service that
 * is fronted by the API gateway.
 */
public final class OpenApiSecuritySchemes {
    /**
     * Bearer-token scheme used when calling services through the gateway.
     * Auth Service issues an opaque access token; the gateway introspects
     * it and converts it to {@link #USER_ID_SCHEME} for the downstream
     * service. From a public API consumer's perspective this is the only
     * scheme that matters.
     */
    public static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Identifier of the API-key scheme that maps to {@code SecurityHeaders.USER_ID}
     * (i.e. {@code X-User-Id}). Gateway-internal; advertised on direct
     * (per-port) Swagger UIs so service authors can poke endpoints
     * without going through the gateway.
     */
    public static final String USER_ID_SCHEME = "userIdHeader";

    private OpenApiSecuritySchemes() {}
}
