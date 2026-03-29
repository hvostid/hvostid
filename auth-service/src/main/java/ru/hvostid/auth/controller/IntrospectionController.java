package ru.hvostid.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.auth.dto.ErrorResponse;
import ru.hvostid.auth.dto.IntrospectRequest;
import ru.hvostid.auth.dto.IntrospectResponse;
import ru.hvostid.auth.service.AuthService;

/**
 * Internal endpoint for token introspection.
 * Called by Gateway on every protected request; NOT routed through Gateway itself.
 */
@Tag(name = "Internal")
@RestController
@RequestMapping(value = "/internal/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class IntrospectionController {
    private static final Logger log = LoggerFactory.getLogger(IntrospectionController.class);

    private final AuthService authService;

    public IntrospectionController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Validate an access token and return user info if active.
     */
    @Operation(
            summary = "Token introspection (internal only)",
            description = "Validates an access token and returns user info if active. This endpoint is NOT routed through the Gateway. Called by Gateway on every protected request via internal network.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Introspection result",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = IntrospectResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/introspect")
    public ResponseEntity<IntrospectResponse> introspect(
            @Valid @RequestBody IntrospectRequest request) {
        log.debug("POST /internal/auth/introspect");
        IntrospectResponse response = authService.introspect(request);
        log.debug("Introspect response: active={}", response.active());
        return ResponseEntity.ok(response);
    }
}
