package ru.hvostid.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.common.security.GatewaySecurityDefaults;

/**
 * Spring Security configuration.
 * Uses gateway-provided headers as pre-authenticated identity.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthTokenProperties.class)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) {
        return GatewaySecurityDefaults.applyTo(http, authenticationManager)
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .requestMatchers("/internal/**")
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
