package ru.hvostid.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.auth.dto.IntrospectRequest;
import ru.hvostid.auth.dto.IntrospectResponse;
import ru.hvostid.auth.service.AuthService;

/**
 * Internal endpoint for token introspection.
 * Called by Gateway on every protected request; NOT routed through Gateway itself.
 */
@RestController
@RequestMapping("/internal/auth")
public class IntrospectionController {
    private final AuthService authService;

    public IntrospectionController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Validate an access token and return user info if active.
     */
    @PostMapping("/introspect")
    public ResponseEntity<IntrospectResponse> introspect(
            @Valid @RequestBody IntrospectRequest request) {
        IntrospectResponse response = authService.introspect(request);
        return ResponseEntity.ok(response);
    }
}
