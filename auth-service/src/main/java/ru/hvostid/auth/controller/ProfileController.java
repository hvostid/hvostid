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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.hvostid.auth.dto.AddRoleRequest;
import ru.hvostid.auth.dto.ErrorResponse;
import ru.hvostid.auth.dto.ProfileResponse;
import ru.hvostid.auth.dto.UpdateProfileRequest;
import ru.hvostid.auth.service.ProfileService;

/**
 * REST controller for user profile and role management.
 * User identity is provided by Gateway via X-User-Id header.
 */
@Tag(name = "Profile")
@RestController
@RequestMapping(value = "/api/v1/profile", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProfileController {
    static final String USER_ID_HEADER = "X-User-Id";
    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Get the current user's profile.
     */
    @Operation(
            summary = "Get current user profile",
            description = "Returns the profile of the user identified by X-User-Id header "
                    + "(set by Gateway after token introspection).")
    @ApiResponse(
            responseCode = "200",
            description = "Profile retrieved successfully",
            content = @Content(schema = @Schema(implementation = ProfileResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing X-User-Id header",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getMyProfile(
            @Parameter(description = "User ID from Gateway", required = true)
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        log.debug("GET /api/v1/profile/me userId={}", userId);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        ProfileResponse response = profileService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update the current user's profile.
     */
    @Operation(
            summary = "Update current user profile",
            description = "Updates editable profile fields (name, phone, city, bio). "
                    + "Only non-null fields are applied.")
    @ApiResponse(
            responseCode = "200",
            description = "Profile updated successfully",
            content = @Content(schema = @Schema(implementation = ProfileResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing X-User-Id header",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateMyProfile(
            @Parameter(description = "User ID from Gateway", required = true)
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        log.debug("PUT /api/v1/profile/me userId={}", userId);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        ProfileResponse response = profileService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Add a role to the current user.
     * Only 'seller' can be self-assigned. Moderator and admin roles
     * require admin assignment.
     */
    @Operation(
            summary = "Add a role to current user",
            description = "Allows a user to self-assign the 'seller' role. "
                    + "Roles 'moderator' and 'admin' can only be assigned by an administrator.")
    @ApiResponse(
            responseCode = "200",
            description = "Role added successfully",
            content = @Content(schema = @Schema(implementation = ProfileResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing X-User-Id header",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "Cannot self-assign this role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/me/roles")
    public ResponseEntity<ProfileResponse> addRole(
            @Parameter(description = "User ID from Gateway", required = true)
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
            @Valid @RequestBody AddRoleRequest request) {
        log.debug("POST /api/v1/profile/me/roles userId={} role={}", userId, request.role());
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        ProfileResponse response = profileService.addRole(userId, request);
        return ResponseEntity.ok(response);
    }
}
