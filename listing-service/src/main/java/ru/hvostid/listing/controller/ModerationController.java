package ru.hvostid.listing.controller;

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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.common.dto.ErrorResponse;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.listing.dto.FlagListingResponse;
import ru.hvostid.listing.dto.FlagReviewRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ModeratedListingDetailResponse;
import ru.hvostid.listing.dto.ModerationRejectRequest;
import ru.hvostid.listing.service.ModerationService;

@RestController
@RequestMapping("/api/v1/moderation")
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
@Tag(name = "Moderation")
public class ModerationController {
    private static final Logger log = LoggerFactory.getLogger(ModerationController.class);

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Operation(
            summary = "List listings awaiting moderation",
            description = "Paginated feed of listings currently in MODERATION status.")
    @ApiResponse(
            responseCode = "200",
            description = "Listings in moderation (may be empty)",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiResponse(responseCode = "401", description = "Missing authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "Caller is not MODERATOR or ADMIN", content = @Content)
    @GetMapping("/listings")
    public ResponseEntity<Page<ListingResponse>> getListingsInModeration(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /api/v1/moderation/listings page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(moderationService.getListingsInModeration(pageable));
    }

    @Operation(
            summary = "Listing detail with all flags",
            description = "Returns listing data plus the full flag history for moderator review.")
    @ApiResponse(
            responseCode = "200",
            description = "Listing detail",
            content = @Content(schema = @Schema(implementation = ModeratedListingDetailResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "Caller is not MODERATOR or ADMIN", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Listing not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/listings/{id}")
    public ResponseEntity<ModeratedListingDetailResponse> getListingDetail(
            @Parameter(description = "Listing ID", required = true, example = "42") @PathVariable Long id) {
        log.debug("GET /api/v1/moderation/listings/{}", id);
        return ResponseEntity.ok(moderationService.getListingDetail(id));
    }

    @Operation(
            summary = "Approve a listing (MODERATION -> PUBLISHED)",
            description = "Publishes a listing currently in MODERATION status.")
    @ApiResponse(
            responseCode = "200",
            description = "Listing approved",
            content = @Content(schema = @Schema(implementation = ListingResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "Caller is not MODERATOR or ADMIN", content = @Content)
    @ApiResponse(responseCode = "404", description = "Listing not found", content = @Content)
    @ApiResponse(responseCode = "422", description = "Listing is not in MODERATION status", content = @Content)
    @PostMapping("/listings/{id}/approve")
    public ResponseEntity<ListingResponse> approveListing(
            @Parameter(description = "Listing ID", required = true, example = "42") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        Set<String> roles = currentRoles(user);
        log.debug("POST /api/v1/moderation/listings/{}/approve userId={} roles={}", id, userId, roles);
        return ResponseEntity.ok(moderationService.approveListing(id, userId, roles));
    }

    @Operation(
            summary = "Reject a listing back to DRAFT with a comment",
            description = "Returns the listing to the seller as a DRAFT, recording the moderator's comment.")
    @ApiResponse(
            responseCode = "200",
            description = "Listing returned to DRAFT",
            content = @Content(schema = @Schema(implementation = ListingResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing comment", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "Caller is not MODERATOR or ADMIN", content = @Content)
    @ApiResponse(responseCode = "404", description = "Listing not found", content = @Content)
    @ApiResponse(responseCode = "422", description = "Listing is not in MODERATION status", content = @Content)
    @PostMapping("/listings/{id}/reject")
    public ResponseEntity<ListingResponse> rejectListing(
            @Parameter(description = "Listing ID", required = true, example = "42") @PathVariable Long id,
            @Valid @RequestBody ModerationRejectRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        Set<String> roles = currentRoles(user);
        log.debug("POST /api/v1/moderation/listings/{}/reject userId={} roles={}", id, userId, roles);
        return ResponseEntity.ok(moderationService.rejectListing(id, userId, roles, request.comment()));
    }

    @Operation(
            summary = "List pending flags",
            description = "Paginated feed of flags awaiting moderator decision (status=PENDING).")
    @ApiResponse(
            responseCode = "200",
            description = "Pending flags (may be empty)",
            content = @Content(schema = @Schema(implementation = Page.class)))
    @ApiResponse(responseCode = "401", description = "Missing authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "Caller is not MODERATOR or ADMIN", content = @Content)
    @GetMapping("/flags")
    public ResponseEntity<Page<FlagListingResponse>> getPendingFlags(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /api/v1/moderation/flags page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(moderationService.getPendingFlags(pageable));
    }

    @Operation(summary = "Review a flag", description = "Sets a PENDING flag to REVIEWED or DISMISSED.")
    @ApiResponse(
            responseCode = "200",
            description = "Flag reviewed",
            content = @Content(schema = @Schema(implementation = FlagListingResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Decision must be REVIEWED or DISMISSED, or flag is no longer PENDING",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "Caller is not MODERATOR or ADMIN", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Flag not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/flags/{id}/review")
    public ResponseEntity<FlagListingResponse> reviewFlag(
            @Parameter(description = "Flag ID", required = true, example = "1") @PathVariable Long id,
            @Valid @RequestBody FlagReviewRequest request) {
        log.debug("POST /api/v1/moderation/flags/{}/review decision={}", id, request.decision());
        return ResponseEntity.ok(moderationService.reviewFlag(id, request.decision()));
    }

    private Set<String> currentRoles(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(authority -> authority.replace("ROLE_", ""))
                .collect(Collectors.toSet());
    }
}
