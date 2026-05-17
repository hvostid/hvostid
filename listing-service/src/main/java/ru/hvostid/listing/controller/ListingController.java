package ru.hvostid.listing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.hvostid.common.dto.ErrorResponse;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.listing.dto.FlagListingRequest;
import ru.hvostid.listing.dto.FlagListingResponse;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.dto.StatusUpdateRequest;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.exception.UnauthorizedException;
import ru.hvostid.listing.service.ListingFlagService;
import ru.hvostid.listing.service.ListingService;

@RestController
@Validated
@RequestMapping("/api/v1/listings")
@Tag(name = "Listings")
public class ListingController {
    private static final Logger log = LoggerFactory.getLogger(ListingController.class);

    private final ListingService listingService;
    private final ListingFlagService listingFlagService;

    public ListingController(ListingService listingService, ListingFlagService listingFlagService) {
        this.listingService = listingService;
        this.listingFlagService = listingFlagService;
    }

    @Operation(
            summary = "Create a new listing",
            description =
                    "Creates a new animal listing with DRAFT status. Only users with SELLER role can create listings.")
    @ApiResponse(
            responseCode = "201",
            description = "Listing created successfully",
            content = @Content(schema = @Schema(implementation = ListingResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "User does not have SELLER role", content = @Content)
    @PostMapping
    @PreAuthorize("hasRole(T(ru.hvostid.common.security.UserRole).SELLER.value())")
    public ResponseEntity<ListingResponse> createListing(
            @Valid @RequestBody ListingRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        log.debug("POST /api/v1/listings, userId={}", userId);
        ListingResponse response = listingService.createListing(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get listing by ID",
            description =
                    "Returns a listing. Published listings are visible to authenticated users. Draft/Moderation listings are visible only to the owner.")
    @ApiResponse(
            responseCode = "200",
            description = "Listing found",
            content = @Content(schema = @Schema(implementation = ListingResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Access denied (draft listing belongs to another user)",
            content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Listing not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<ListingResponse> getListing(
            @Parameter(description = "Listing ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        log.debug("GET /api/v1/listings/{}, userId={}", id, userId);

        ListingResponse response = listingService.getListing(id, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Update a listing",
            description =
                    "Updates an existing listing. Only the owner can update. Only listings with DRAFT or PUBLISHED status can be updated. All fields are optional.")
    @ApiResponse(
            responseCode = "200",
            description = "Listing updated successfully",
            content = @Content(schema = @Schema(implementation = ListingResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error or cannot edit listing in current status",
            content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "User is not the owner of this listing", content = @Content)
    @ApiResponse(responseCode = "404", description = "Listing not found", content = @Content)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole(T(ru.hvostid.common.security.UserRole).SELLER.value())")
    public ResponseEntity<ListingResponse> updateListing(
            @Parameter(description = "Listing ID", required = true, example = "1") @PathVariable Long id,
            @Valid @RequestBody ListingUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        log.debug("PUT /api/v1/listings/{}, userId={}", id, userId);

        ListingResponse response = listingService.updateListing(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get listings",
            description = "Without parameters returns published listings. "
                    + "Use 'q' for full-text search over published listings. "
                    + "Use 'my=true' (auth required) to fetch the caller's own listings in any status; "
                    + "combine with 'status' to filter, e.g. 'my=true&status=ARCHIVED'.")
    @ApiResponse(
            responseCode = "200",
            description = "Paginated listings (may be empty)",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiResponse(
            responseCode = "401",
            description = "'my=true' was requested without an authenticated user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public ResponseEntity<Page<ListingResponse>> getListings(
            @RequestParam(value = "q", required = false)
                    @Size(max = 500, message = "Search query too long, max 500 characters")
                    String keyword,
            @Parameter(description = "Return only listings owned by the authenticated caller")
                    @RequestParam(value = "my", required = false, defaultValue = "false")
                    boolean my,
            @Parameter(description = "Filter by listing status (only honored together with my=true)")
                    @RequestParam(value = "status", required = false)
                    ListingStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {

        log.debug(
                "GET /api/v1/listings, keyword='{}', my={}, status={}, page={}, size={}",
                keyword,
                my,
                status,
                pageable.getPageNumber(),
                pageable.getPageSize());

        int maxSize = 100;
        if (pageable.getPageSize() > maxSize) {
            pageable = PageRequest.of(pageable.getPageNumber(), maxSize, pageable.getSort());
        }

        if (my) {
            if (user == null) {
                throw new UnauthorizedException("Authentication is required for 'my=true'");
            }
            long userId = GatewayPreAuthentication.currentUserId(user);
            return ResponseEntity.ok(listingService.getMyListings(userId, status, pageable));
        }

        Page<ListingResponse> responses = listingService.searchListings(keyword, pageable);
        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Change listing status",
            description =
                    "Allowed transitions: DRAFT->MODERATION, MODERATION->PUBLISHED/REJECTED/DRAFT, PUBLISHED->ARCHIVED/SOLD, REJECTED->MODERATION/DRAFT")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Not owner or not moderator")
    @ApiResponse(responseCode = "404", description = "Listing not found")
    @ApiResponse(responseCode = "422", description = "Invalid status transition")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ListingResponse> updateStatus(
            @Parameter(description = "Listing ID", required = true, example = "1") @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {

        long userId = GatewayPreAuthentication.currentUserId(user);

        // Extract roles without ROLE_ prefix
        Set<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(role -> role.replace("ROLE_", ""))
                .collect(Collectors.toSet());

        log.debug(
                "PATCH /api/v1/listings/{}/status, userId={}, roles={}, targetStatus={}",
                id,
                userId,
                roles,
                request.status());

        ListingResponse response = listingService.updateStatus(id, request, userId, roles);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Report a listing as problematic",
            description =
                    "Submits a flag against a published listing. The reporter must be authenticated and cannot flag their own listing. A user may submit at most one flag per listing. When the listing accumulates 3+ pending flags, it is automatically moved to MODERATION.")
    @ApiResponse(
            responseCode = "201",
            description = "Flag created",
            content = @Content(schema = @Schema(implementation = FlagListingResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error or listing not in a flaggable state",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "Listing owner cannot flag their own listing", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Listing not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "User has already flagged this listing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{id}/flag")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FlagListingResponse> flagListing(
            @Parameter(description = "Listing ID", required = true, example = "1") @PathVariable Long id,
            @Valid @RequestBody FlagListingRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        log.debug("POST /api/v1/listings/{}/flag, userId={}, reason={}", id, userId, request.reason());

        FlagListingResponse response = listingFlagService.flagListing(id, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
