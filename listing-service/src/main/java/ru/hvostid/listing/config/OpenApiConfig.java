package ru.hvostid.listing.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hvostid.common.openapi.HvostidOpenApi;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI listingOpenAPI() {
        return HvostidOpenApi.gatewayFronted(
                "Listing Service API", "Manage animal listings (CRUD operations)", "1.0.0", 8082);
    }
}
