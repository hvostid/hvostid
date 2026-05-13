package ru.hvostid.matching.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hvostid.common.openapi.HvostidOpenApi;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI matchingOpenAPI() {
        return HvostidOpenApi.gatewayFronted(
                "Matching Service API", "Buyer questionnaire and pet matching", "1.0.0", 8084);
    }
}
