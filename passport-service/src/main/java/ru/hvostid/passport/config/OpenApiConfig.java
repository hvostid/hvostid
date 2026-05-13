package ru.hvostid.passport.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hvostid.common.openapi.HvostidOpenApi;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI passportOpenAPI() {
        return HvostidOpenApi.gatewayFronted(
                "Passport Service API", "Manage digital pet passports and related passport data", "1.0.0", 8083);
    }
}
