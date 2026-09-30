package ru.hvostid.listing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.data.domain.Sort;
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
import ru.hvostid.listing.ListingConstants;
import ru.hvostid.listing.dto.FlagListingRequest;
import ru.hvostid.listing.dto.FlagListingResponse;
import ru.hvostid.listing.dto.ListingFilterRequest;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.dto.PassportPublishedStatusResponse;
import ru.hvostid.listing.dto.StatusUpdateRequest;
import ru.hvostid.listing.entity.ListingStatus;
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
        Long userId = GatewayPreAuthentication.currentUserIdOrNull(user);
        Set<String> roles = user == null ? Set.of() : currentRoles(user);
        log.debug("GET /api/v1/listings/{}, userId={}, roles={}", id, userId, roles);

        ListingResponse response = listingService.getListing(id, userId, roles);
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
            summary = "Browse the public listings catalog",
            description = "Returns PUBLISHED listings. "
                    + "Use 'q' for full-text search (sorted by relevance). "
                    + "Optional filters: species, breed, ageMin/ageMax, priceMin/priceMax, city, "
                    + "plus 'sort' (price_asc, price_desc, created_desc). "
                    + "Anonymous-friendly: requires no authentication.")
    @ApiResponse(
            responseCode = "200",
            description = "Paginated listings (may be empty)",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Validation error or incompatible parameter combination",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public ResponseEntity<Page<ListingResponse>> getListings(
            @RequestParam(value = "q", required = false)
                    @Size(max = 500, message = "Search query too long, max 500 characters")
                    String keyword,
            @RequestParam(required = false) @Size(max = 100, message = "Species too long, max 100 characters")
                    String species,
            @RequestParam(required = false) @Size(max = 100, message = "Breed too long, max 100 characters")
                    String breed,
            @RequestParam(required = false) @Min(ListingConstants.MIN_AGE) @Max(ListingConstants.MAX_AGE_MONTHS)
                    Integer ageMin,
            @RequestParam(required = false) @Min(ListingConstants.MIN_AGE) @Max(ListingConstants.MAX_AGE_MONTHS)
                    Integer ageMax,
            @RequestParam(required = false) @Min(0) @Max(999999999) Integer priceMin,
            @RequestParam(required = false) @Min(0) @Max(999999999) Integer priceMax,
            @RequestParam(required = false) @Size(max = 100, message = "City too long, max 100 characters") String city,
            @RequestParam(defaultValue = "created_desc")
                    @Pattern(
                            regexp = "^(price_asc|price_desc|created_desc)$",
                            message = "Sort must be one of: price_asc, price_desc, created_desc")
                    String sort,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        boolean hasKeyword = keyword != null && !keyword.isBlank() && !"\"\"".equals(keyword.trim());
        ListingFilterRequest filters =
                new ListingFilterRequest(species, breed, ageMin, ageMax, priceMin, priceMax, city);

        log.debug(
                "GET /api/v1/listings, hasKeyword={}, filters={}, sort={}, page={}, size={}",
                hasKeyword,
                filters,
                sort,
                pageable.getPageNumber(),
                pageable.getPageSize());

        if (pageable.getPageSize() > ListingConstants.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size cannot exceed " + ListingConstants.MAX_PAGE_SIZE);
        }

        if (ageMin != null && ageMax != null && ageMin > ageMax) {
            throw new IllegalArgumentException("ageMin must be less than or equal to ageMax");
        }
        if (priceMin != null && priceMax != null && priceMin > priceMax) {
            throw new IllegalArgumentException("priceMin must be less than or equal to priceMax");
        }

        // The native search query owns relevance ordering. Spring's pageable resolver
        // also reads 'sort', so discard its ordering before executing keyword search.
        Pageable effectivePageable = hasKeyword
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), parseSort(sort));

        Page<ListingResponse> responses = hasKeyword
                ? listingService.searchWithFilters(keyword, filters, effectivePageable)
                : listingService.getListingsWithFilters(filters, effectivePageable);
        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "List the caller's own listings",
            description = "Returns every listing owned by the authenticated caller in any status. "
                    + "Optional 'status' filter narrows results to one status (e.g. ARCHIVED, SOLD).")
    @ApiResponse(
            responseCode = "200",
            description = "Paginated listings (may be empty)",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid authenticated user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ListingResponse>> getMyListings(
            @Parameter(description = "Filter by listing status") @RequestParam(value = "status", required = false)
                    ListingStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {

        if (pageable.getPageSize() > ListingConstants.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size cannot exceed " + ListingConstants.MAX_PAGE_SIZE);
        }

        long userId = GatewayPreAuthentication.currentUserId(user);
        log.debug(
                "GET /api/v1/listings/my, userId={}, status={}, page={}, size={}",
                userId,
                status,
                pageable.getPageNumber(),
                pageable.getPageSize());
        return ResponseEntity.ok(listingService.getMyListings(userId, status, pageable));
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
        Set<String> roles = currentRoles(user);

        log.debug(
                "PATCH /api/v1/listings/{}/status, userId={}, roles={}, targetStatus={}",
                id,
                userId,
                roles,
                request.status());

        ListingResponse response = listingService.updateStatus(id, request, userId, roles);
        return ResponseEntity.ok(response);
    }

    private Set<String> currentRoles(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .collect(Collectors.toSet());
    }

    private Sort parseSort(String sort) {
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
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

    @Operation(
            summary = "Check if a passport is referenced by a PUBLISHED listing",
            description =
                    "Returns true when at least one PUBLISHED listing references the given passport id. Used by passport-service to gate buyer-facing endpoints that would otherwise leak passport existence. Existence-only signal; PUBLISHED listings are already public.")
    @ApiResponse(
            responseCode = "200",
            description = "Status returned (hasPublishedListing may be true or false)",
            content = @Content(schema = @Schema(implementation = PassportPublishedStatusResponse.class)))
    @GetMapping("/passports/{passportId}/has-published")
    public ResponseEntity<PassportPublishedStatusResponse> hasPublishedListingForPassport(
            @Parameter(description = "Passport identifier as stored on the listing", required = true, example = "42")
                    @PathVariable
                    String passportId,
            @Parameter(description = "Require the listing to belong to this passport owner")
                    @RequestParam(required = false)
                    Long sellerId) {
        log.debug("GET /api/v1/listings/passports/{}/has-published", passportId);
        boolean exists = listingService.hasPublishedListingForPassport(passportId, sellerId);
        return ResponseEntity.ok(new PassportPublishedStatusResponse(passportId, exists));
    }

    @Operation(
            summary = "Permanently delete a listing",
            description = "Hard-deletes a listing owned by the caller. " + "Only the owner or ADMIN can delete. "
                    + "Listings in MODERATION state cannot be deleted. "
                    + "Deletion cascades to flags and status history.")
    @ApiResponse(responseCode = "204", description = "Listing deleted successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "Not owner and not ADMIN", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Listing not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Listing is under moderation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<Void> deleteListing(
            @Parameter(description = "Listing ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {

        long userId = GatewayPreAuthentication.currentUserId(user);
        Set<String> roles = currentRoles(user);

        log.debug("DELETE /api/v1/listings/{}, userId={}, roles={}", id, userId, roles);

        listingService.deleteListing(id, userId, roles);
        return ResponseEntity.noContent().build();
    }
}
