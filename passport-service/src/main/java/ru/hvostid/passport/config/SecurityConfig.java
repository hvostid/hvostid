package ru.hvostid.passport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.common.security.GatewaySecurityDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    /**
     * Ticket-backed media endpoint. The gateway lists this path as public
     * (hvostid.auth.public-paths); without the matching permit here the
     * service-level filter chain would reject the request with 401 even
     * though the ticket query parameter is what gates access.
     */
    private static final String MEDIA_CONTENT_PATH = "/api/v1/passports/*/docs/*/content";

    /**
     * Public catalog cover photo endpoint. Same rationale as
     * {@link #MEDIA_CONTENT_PATH}: must be permitted both on the gateway and
     * here. Access is gated by the listing-service check inside the
     * controller, which only resolves for passports backed by a PUBLISHED
     * listing.
     */
    private static final String MEDIA_COVER_PATH = "/api/v1/passports/*/cover";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) {
        return GatewaySecurityDefaults.applyTo(http, authenticationManager)
                .authorizeHttpRequests(auth -> auth.requestMatchers(GatewaySecurityDefaults.internalPaths())
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers(GatewaySecurityDefaults.alwaysPublic())
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, MEDIA_CONTENT_PATH, MEDIA_COVER_PATH)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return GatewayPreAuthentication.authenticationManager();
    }
}
