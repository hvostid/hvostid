package ru.hvostid.matching.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.common.dto.ErrorResponse;
import ru.hvostid.common.http.SecurityHeaders;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.matching.dto.RecommendationsResponse;
import ru.hvostid.matching.service.MatchRecommendationsService;

@RestController
@Validated
@RequestMapping("/api/v1/match/recommendations")
@Tag(name = "Compatibility score")
public class MatchRecommendationsController {
    private static final Logger log = LoggerFactory.getLogger(MatchRecommendationsController.class);
    private static final int DEFAULT_MIN_SCORE = 40;
    private static final int MAX_PAGE_SIZE = 50;

    private final MatchRecommendationsService recommendationsService;

    public MatchRecommendationsController(MatchRecommendationsService recommendationsService) {
        this.recommendationsService = recommendationsService;
    }

    @Operation(
            summary = "Get top compatible listings for the buyer",
            description = "Returns PUBLISHED listings ranked by compatibility score against the authenticated buyer's "
                    + "questionnaire. Listings are sorted by score descending; only entries with score >= minScore "
                    + "are returned. Results are cached per buyer for 10 minutes.")
    @ApiResponse(
            responseCode = "200",
            description = "Recommendations",
            content = @Content(schema = @Schema(implementation = RecommendationsResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Buyer has not submitted a questionnaire",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(
            responseCode = "503",
            description = "Listing service unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RecommendationsResponse> getRecommendations(
            @Parameter(description = "Zero-based page index", example = "0") @RequestParam(defaultValue = "0") @Min(0)
                    int page,
            @Parameter(description = "Page size (1..50)", example = "10")
                    @RequestParam(defaultValue = "10")
                    @Min(1)
                    @Max(MAX_PAGE_SIZE)
                    int size,
            @Parameter(description = "Minimum compatibility score (0..100); defaults to 40", example = "50")
                    @RequestParam(defaultValue = "" + DEFAULT_MIN_SCORE)
                    @Min(0)
                    @Max(100)
                    int minScore,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            HttpServletRequest httpRequest) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        String requestId = httpRequest.getHeader(SecurityHeaders.REQUEST_ID);
        log.debug(
                "GET /api/v1/match/recommendations userId={} page={} size={} minScore={}",
                userId,
                page,
                size,
                minScore);
        return ResponseEntity.ok(recommendationsService.getRecommendations(userId, minScore, page, size, requestId));
    }
}
