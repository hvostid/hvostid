package ru.hvostid.listing.config;

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

/**
 * Spring Security configuration.
 * Uses gateway-provided headers as pre-authenticated identity.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager)
            throws Exception {
        return GatewaySecurityDefaults.applyTo(http, authenticationManager)
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/api/v1/listings")
                        .permitAll()
                        .requestMatchers(GatewaySecurityDefaults.alwaysPublic())
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
