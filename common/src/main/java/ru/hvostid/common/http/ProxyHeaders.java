package ru.hvostid.common.http;

/**
 * HTTP header names used to control the frontend nginx that fronts
 * Spring Boot services.
 *
 * <p>Kept separate from {@link SecurityHeaders}, which carries authenticated
 * identity between the gateway and downstream services; this class is about
 * response side directives the gateway-fronted nginx interprets.
 */
public final class ProxyHeaders {
    /**
     * Nginx internal redirect. When the upstream returns this header on a
     * response, nginx discards the response body and serves the content
     * pointed at by the header value via an internal subrequest.
     *
     * <p>Used by the passport service media flow to hand off byte streaming
     * to nginx so application threads never touch image bytes.
     */
    public static final String X_ACCEL_REDIRECT = "X-Accel-Redirect";

    /**
     * Standard response header that limits how much referrer information
     * browsers attach to outgoing requests. Set to {@code no-referrer} on
     * ticketed media responses so the ticketed URL does not leak to third
     * party origins via the {@code Referer} request header.
     */
    public static final String REFERRER_POLICY = "Referrer-Policy";

    private ProxyHeaders() {
        // utility class
    }
}
