package ru.hvostid.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import ru.hvostid.common.http.SecurityHeaders;

/**
 * Factory for the {@link OpenAPI} bean shared by every backend service.
 *
 * <p>All five services sit behind the API Gateway and share the same security
 * model: a Bearer access token introspected by the gateway, which then injects
 * {@code X-User-Id} for the downstream service. This factory wires both schemes
 * (with {@code bearerAuth} as the default), and adds the gateway + direct
 * server entries so the Swagger UI server dropdown is consistent across specs.
 */
public final class HvostidOpenApi {
    private static final String GATEWAY_URL = "http://localhost:8080";
    private static final String GATEWAY_SERVER_DESCRIPTION = "API Gateway (local)";

    private HvostidOpenApi() {}

    /**
     * Build the {@link OpenAPI} for the API Gateway itself.
     *
     * <p>The gateway aggregates per-service specs via {@code springdoc.swagger-ui.urls};
     * its own spec only carries the Info block and a localhost server entry. No
     * security scheme is registered because the gateway does not expose any
     * documented endpoints of its own.
     */
    public static OpenAPI gateway(String title, String description, String version) {
        return new OpenAPI()
                .info(new Info().title(title).description(description).version(version))
                .addServersItem(new Server().url(GATEWAY_URL).description(GATEWAY_SERVER_DESCRIPTION));
    }

    /**
     * Build the {@link OpenAPI} for a gateway-fronted service.
     *
     * @param title           human-readable spec title (e.g. "Listing Service API")
     * @param description     short description shown at the top of the Swagger UI
     * @param version         service API version (e.g. "1.0.0")
     * @param directPort      the port the service exposes on localhost (e.g. 8082)
     * @param directHint      extra context for the {@code userIdHeader} description
     *                        (typically just empty; auth-service points at /profile)
     */
    public static OpenAPI gatewayFronted(
            String title, String description, String version, int directPort, String directHint) {
        String directServerDescription = title + " (direct)";
        String userIdSchemeDescription = "Gateway-injected identity header. "
                + "Only set this directly when bypassing the gateway "
                + "(per-port Swagger UI on :" + directPort + (directHint.isEmpty() ? "" : ", " + directHint) + ").";
        return new OpenAPI()
                .info(new Info().title(title).description(description).version(version))
                .addServersItem(new Server().url(GATEWAY_URL).description(GATEWAY_SERVER_DESCRIPTION))
                .addServersItem(
                        new Server().url("http://localhost:" + directPort).description(directServerDescription))
                .addSecurityItem(new SecurityRequirement().addList(OpenApiSecuritySchemes.BEARER_SCHEME))
                .schemaRequirement(OpenApiSecuritySchemes.BEARER_SCHEME, bearerScheme())
                .schemaRequirement(OpenApiSecuritySchemes.USER_ID_SCHEME, userIdScheme(userIdSchemeDescription));
    }

    /** Convenience overload for services with no extra direct-mode hint. */
    public static OpenAPI gatewayFronted(String title, String description, String version, int directPort) {
        return gatewayFronted(title, description, version, directPort, "");
    }

    private static SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("opaque")
                .description("Opaque access token issued by Auth Service. "
                        + "Required when calling through the API Gateway.");
    }

    private static SecurityScheme userIdScheme(String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(SecurityHeaders.USER_ID)
                .description(description);
    }
}
