package ru.hvostid.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hvostid.common.http.SecurityHeaders;
import ru.hvostid.common.openapi.OpenApiSecuritySchemes;

/**
 * OpenAPI / Swagger UI configuration for Auth Service.
 * Declares both security schemes used by the platform: {@code bearerAuth}
 * (the public scheme honored by the API Gateway) and {@code userIdHeader}
 * (the gateway-internal pre-auth header consumed directly on port 8081 by
 * {@code /api/v1/profile/**} endpoints).
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
                .schemaRequirement(
                        OpenApiSecuritySchemes.BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque")
                                .description("Opaque access token issued by Auth Service. "
                                        + "Required when calling through the API Gateway."))
                .schemaRequirement(
                        OpenApiSecuritySchemes.USER_ID_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(SecurityHeaders.USER_ID)
                                .description("Gateway-injected identity header. "
                                        + "Only set this directly when bypassing the gateway "
                                        + "(per-port Swagger UI on :8081, /api/v1/profile/** endpoints)."));
    }
}
