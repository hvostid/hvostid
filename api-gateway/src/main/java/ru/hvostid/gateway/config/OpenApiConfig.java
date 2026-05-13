package ru.hvostid.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hvostid.common.openapi.HvostidOpenApi;

/**
 * OpenAPI metadata for the API Gateway itself. The aggregated Swagger UI
 * that lists every downstream service is configured declaratively in
 * application.yml under {@code springdoc.swagger-ui.urls}; each entry
 * points at a {@code /v3/api-docs/<service>} route on this gateway which
 * forwards to the corresponding service's own spec endpoint.
 */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI gatewayOpenApi() {
        return HvostidOpenApi.gateway(
                "HvostID API Gateway",
                "Entry point that fronts every backend service. "
                        + "The Swagger UI lets you switch between per-service specs "
                        + "via the definition dropdown.",
                "1.0.0");
    }
}
