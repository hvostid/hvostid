package ru.hvostid.passport.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hvostid.common.http.SecurityHeaders;
import ru.hvostid.common.openapi.OpenApiSecuritySchemes;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI passportOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Passport Service API")
                        .description("Manage digital pet passports and related passport data")
                        .version("1.0.0"))
                .addServersItem(new Server().url("http://localhost:8080").description("API Gateway (local)"))
                .addServersItem(new Server().url("http://localhost:8083").description("Passport Service (direct)"))
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
                                        + "(per-port Swagger UI on :8083)."));
    }
}
