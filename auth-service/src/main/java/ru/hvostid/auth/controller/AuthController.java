package ru.hvostid.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.hvostid.auth.dto.*;
import ru.hvostid.auth.service.AuthService;

/**
 * REST controller for authentication endpoints.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Register a new user account.
     */
    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account with BUYER role by default.")
    @ApiResponse(
            responseCode = "201",
            description = "User created successfully",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Email already registered",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("POST /api/v1/auth/register email={}", request.email());
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate and obtain access/refresh tokens.
     */
    @Operation(
            summary = "Log in with email and password",
            description = "Authenticates the user and returns opaque access and refresh tokens. Tokens are random strings stored in the database (not JWT).")
    @ApiResponse(
            responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(
            responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.debug("POST /api/v1/auth/login email={}", request.email());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh tokens using a valid refresh token.
     * Generates a new access + refresh pair and invalidates the old session.
     */
    @Operation(
            summary = "Refresh access token",
            description = "Generates a new access + refresh token pair using a valid refresh token. The old session is deleted (token rotation).")
    @ApiResponse(
            responseCode = "200",
            description = "Tokens refreshed successfully",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Invalid or expired refresh token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.debug("POST /api/v1/auth/refresh");
        LoginResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout by revoking the session associated with the Bearer token.
     */
    @Operation(
            summary = "Logout and revoke session",
            description = "Deletes the session associated with the Bearer token. Subsequent introspection of the token will return active: false.")
    @ApiResponse(
            responseCode = "204",
            description = "Session revoked successfully",
            content = @Content)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(
                    description = "Bearer token",
                    example = "Bearer dGhpcyBpcyBhIHNhbXBsZSB0b2tlbg",
                    required = true)
            @RequestHeader("Authorization")
            String authHeader) {
        log.debug("POST /api/v1/auth/logout");
        String accessToken = extractBearerToken(authHeader);
        authService.logout(accessToken);
        return ResponseEntity.noContent().build();
    }

    /**
     * Extract the token value from "Bearer <token>" header.
     */
    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return "";
    }
}
