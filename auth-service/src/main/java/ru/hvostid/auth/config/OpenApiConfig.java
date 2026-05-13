package ru.hvostid.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hvostid.common.openapi.HvostidOpenApi;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI authServiceOpenApi() {
        return HvostidOpenApi.gatewayFronted(
                "HvostID Auth Service",
                "Authentication API - registration, login, introspection, refresh, logout with opaque tokens",
                "1.1.0",
                8081,
                "/api/v1/profile/** endpoints");
    }
}
