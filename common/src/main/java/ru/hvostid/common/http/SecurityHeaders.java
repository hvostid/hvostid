package ru.hvostid.common.http;

/**
 * HTTP header names used for inter-service communication.
 * <p>
 * Gateway injects these headers after token introspection so that
 * downstream services can identify the authenticated user.
 */
public final class SecurityHeaders {
    /**
     * User identifier extracted from introspection result.
     */
    public static final String USER_ID = "X-User-Id";

    /**
     * Comma-separated list of user roles (e.g. "BUYER,SELLER").
     */
    public static final String USER_ROLES = "X-User-Roles";

    /**
     * Unique request identifier propagated through all services.
     */
    public static final String REQUEST_ID = "X-Request-Id";

    private SecurityHeaders() {
        // utility class
    }
}
