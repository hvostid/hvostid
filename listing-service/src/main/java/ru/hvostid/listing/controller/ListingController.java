package ru.hvostid.listing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.exception.AccessDeniedException;
import ru.hvostid.listing.service.ListingService;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/listings")
@Tag(name = "Listings")
public class ListingController {
    private static final Logger log = LoggerFactory.getLogger(ListingController.class);

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @Operation(
            summary = "Create a new listing",
            description = "Creates a new animal listing with DRAFT status. Only users with seller role can create listings."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Listing created successfully",
                    content = @Content(schema = @Schema(implementation = ListingResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error or invalid userId",
                    content = @Content(schema = @Schema(implementation = ru.hvostid.listing.exception.GlobalExceptionHandler.ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "User does not have seller role",
                    content = @Content)
    })
    @PostMapping
    public ResponseEntity<ListingResponse> createListing(
            @Valid @RequestBody ListingRequest request,
            @Parameter(description = "User ID from Gateway", required = true, example = "100")
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "User roles from Gateway (comma-separated)", required = true, example = "seller,buyer")
            @RequestHeader("X-User-Roles") String rolesHeader) {

        log.debug("POST /api/v1/listings, userId={}, roles={}", userId, rolesHeader);

        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        if (!hasRole(rolesHeader, "seller")) {
            log.warn("Create listing denied: user {} does not have seller role", userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ListingResponse response = listingService.createListing(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get listing by ID",
            description = "Returns a listing. Published listings are visible to everyone. Draft/Moderation listings are visible only to the owner."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Listing found",
                    content = @Content(schema = @Schema(implementation = ListingResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid userId",
                    content = @Content),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied (draft listing belongs to another user)",
                    content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Listing not found",
                    content = @Content(schema = @Schema(implementation = ru.hvostid.listing.exception.GlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ListingResponse> getListing(
            @Parameter(description = "Listing ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "User ID from Gateway", required = true, example = "100")
            @RequestHeader("X-User-Id") Long userId) {

        log.debug("GET /api/v1/listings/{}, userId={}", id, userId);

        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        ListingResponse response = listingService.getListing(id, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Update a listing",
            description = "Updates an existing listing. Only the owner can update. Only listings with DRAFT or PUBLISHED status can be updated. All fields are optional."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Listing updated successfully",
                    content = @Content(schema = @Schema(implementation = ListingResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error, invalid userId, or cannot edit listing in current status",
                    content = @Content),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not the owner of this listing",
                    content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Listing not found",
                    content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<ListingResponse> updateListing(
            @Parameter(description = "Listing ID", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ListingUpdateRequest request,
            @Parameter(description = "User ID from Gateway", required = true, example = "100")
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "User roles from Gateway (comma-separated)", required = true, example = "seller,buyer")
            @RequestHeader("X-User-Roles") String rolesHeader) {

        log.debug("PUT /api/v1/listings/{}, userId={}", id, userId);

        if (userId == null || userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        if (!hasRole(rolesHeader, "seller")) {
            throw new AccessDeniedException("User does not have seller role");
        }

        ListingResponse response = listingService.updateListing(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get published listings",
            description = "Returns a paginated list of all published listings. Draft, moderation, rejected, and archived listings are excluded."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "List of published listings (may be empty)",
                    content = @Content(schema = @Schema(implementation = Page.class)))
    })
    @GetMapping
    public ResponseEntity<Page<ListingResponse>> getListings(
            @Parameter(description = "Pagination parameters (page number, size, sort)")
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("GET /api/v1/listings, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        int maxSize = 100;
        if (pageable.getPageSize() > maxSize) {
            pageable = PageRequest.of(pageable.getPageNumber(), maxSize, pageable.getSort());
        }

        Page<ListingResponse> responses = listingService.getPublishedListings(pageable);
        return ResponseEntity.ok(responses);
    }

    private boolean hasRole(String rolesHeader, String role) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .anyMatch(r -> r.equalsIgnoreCase(role));
    }
}