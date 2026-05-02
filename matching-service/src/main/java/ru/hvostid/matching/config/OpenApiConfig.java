package ru.hvostid.matching.config;

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
    public OpenAPI matchingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Matching Service API")
                        .description("Buyer questionnaire and pet matching")
                        .version("1.0.0"))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("API Gateway (local)"))
                .addServersItem(new Server()
                        .url("http://localhost:8084")
                        .description("Matching Service (direct)"))
                .addSecurityItem(new SecurityRequirement().addList(OpenApiSecuritySchemes.USER_ID_SCHEME))
                .schemaRequirement(OpenApiSecuritySchemes.USER_ID_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(SecurityHeaders.USER_ID));
    }
}
