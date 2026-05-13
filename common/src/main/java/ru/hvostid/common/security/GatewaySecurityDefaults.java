package ru.hvostid.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Reusable Spring Security baseline for every gateway-fronted backend service.
 *
 * <p>Every service shares the same posture: stateless, CSRF off (no browser
 * form posts), 401 entry point, gateway pre-auth filter ahead of the
 * username/password slot, and the swagger / actuator endpoints open. Service
 * code only has to call {@link #applyTo(HttpSecurity, AuthenticationManager)}
 * and then add its own {@code authorizeHttpRequests} rules.
 */
public final class GatewaySecurityDefaults {
    /** Paths the platform always exposes without authentication. */
    public static final String[] ALWAYS_PUBLIC = {
        "/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"
    };

    private GatewaySecurityDefaults() {}

    /**
     * Apply the shared baseline to {@code http}. Returns the same builder so
     * the caller can chain its service-specific {@code authorizeHttpRequests}.
     */
    public static HttpSecurity applyTo(HttpSecurity http, AuthenticationManager authenticationManager)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (_, response, _) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
                .addFilterBefore(
                        GatewayPreAuthentication.requestHeaderAuthenticationFilter(authenticationManager),
                        UsernamePasswordAuthenticationFilter.class);
    }
}
