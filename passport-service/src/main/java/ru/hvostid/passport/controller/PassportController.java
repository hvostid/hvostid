package ru.hvostid.passport.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.hvostid.common.dto.ErrorResponse;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.passport.dto.CreatePassportRequest;
import ru.hvostid.passport.dto.PassportResponse;
import ru.hvostid.passport.dto.TrustScoreResponse;
import ru.hvostid.passport.dto.UpdatePassportRequest;
import ru.hvostid.passport.service.PassportService;
import ru.hvostid.passport.service.TrustScoreService;

@RestController
@RequestMapping("/api/v1/passports")
@Tag(name = "Pet passports")
public class PassportController {
    private static final Logger log = LoggerFactory.getLogger(PassportController.class);

    private final PassportService passportService;
    private final TrustScoreService trustScoreService;

    public PassportController(PassportService passportService, TrustScoreService trustScoreService) {
        this.passportService = passportService;
        this.trustScoreService = trustScoreService;
    }

    @Operation(
            summary = "Create a pet passport",
            description = "Creates a new pet passport for the authenticated seller.")
    @ApiResponse(
            responseCode = "201",
            description = "Passport created successfully",
            content = @Content(schema = @Schema(implementation = PassportResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "User does not have SELLER role", content = @Content)
    @PostMapping
    @PreAuthorize("hasRole(T(ru.hvostid.common.security.UserRole).SELLER.value())")
    public ResponseEntity<PassportResponse> createPassport(
            @Valid @RequestBody CreatePassportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long sellerId = GatewayPreAuthentication.currentUserId(user);
        log.debug("POST /api/v1/passports, sellerId={}", sellerId);

        PassportResponse response = passportService.createPassport(request, sellerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get a pet passport",
            description =
                    "Returns a pet passport with vaccination entries. Only the owner, moderators, and admins can view the full passport.")
    @ApiResponse(
            responseCode = "200",
            description = "Passport found",
            content = @Content(schema = @Schema(implementation = PassportResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "User is not allowed to view this passport", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Passport not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{petId}")
    public ResponseEntity<PassportResponse> getPassport(
            @Parameter(description = "Pet passport ID", required = true, example = "1") @PathVariable Long petId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        Set<String> roles = currentRoles(user);
        log.debug("GET /api/v1/passports/{}, userId={}, roles={}", petId, userId, roles);

        PassportResponse response = passportService.getPassport(petId, userId, roles);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Update a pet passport",
            description = "Updates an existing pet passport. Only the passport owner can update it.")
    @ApiResponse(
            responseCode = "200",
            description = "Passport updated successfully",
            content = @Content(schema = @Schema(implementation = PassportResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "User is not the owner of this passport", content = @Content)
    @ApiResponse(responseCode = "404", description = "Passport not found", content = @Content)
    @PutMapping("/{petId}")
    @PreAuthorize("hasRole(T(ru.hvostid.common.security.UserRole).SELLER.value())")
    public ResponseEntity<PassportResponse> updatePassport(
            @Parameter(description = "Pet passport ID", required = true, example = "1") @PathVariable Long petId,
            @Valid @RequestBody UpdatePassportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long sellerId = GatewayPreAuthentication.currentUserId(user);
        log.debug("PUT /api/v1/passports/{}, sellerId={}", petId, sellerId);

        PassportResponse response = passportService.updatePassport(petId, request, sellerId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get the trust score for a pet passport",
            description =
                    "Returns a 0-100 trust score with the per-component breakdown. Available to any authenticated user so buyers can evaluate listings.")
    @ApiResponse(
            responseCode = "200",
            description = "Trust score",
            content = @Content(schema = @Schema(implementation = TrustScoreResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Passport not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{petId}/trust")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TrustScoreResponse> getTrustScore(
            @Parameter(description = "Pet passport ID", required = true, example = "1") @PathVariable Long petId) {
        log.debug("GET /api/v1/passports/{}/trust", petId);
        return ResponseEntity.ok(trustScoreService.getTrustScore(petId));
    }

    private Set<String> currentRoles(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(authority -> authority.replace("ROLE_", ""))
                .collect(Collectors.toSet());
    }
}
