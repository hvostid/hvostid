package ru.hvostid.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hvostid.common.openapi.OpenApiSecuritySchemes;

/**
 * OpenAPI / Swagger UI configuration for Auth Service.
 * Registers a Bearer token security scheme so protected endpoints
 * can be tested directly from the Swagger UI.
 */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI authServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HvostID Auth Service")
                        .description(
                                "Authentication API - registration, login, introspection, refresh, logout with opaque tokens")
                        .version("1.1.0"))
                .addServersItem(new Server().url("http://localhost:8080").description("API Gateway (local)"))
                .addServersItem(new Server().url("http://localhost:8081").description("Auth Service (direct)"))
                .addSecurityItem(new SecurityRequirement().addList(OpenApiSecuritySchemes.BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(
                                OpenApiSecuritySchemes.BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(OpenApiSecuritySchemes.BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("opaque")));
    }
}
