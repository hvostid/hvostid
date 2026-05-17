package ru.hvostid.matching.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.common.dto.ErrorResponse;
import ru.hvostid.common.http.SecurityHeaders;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.matching.dto.MatchScoreRequest;
import ru.hvostid.matching.dto.MatchScoreResponse;
import ru.hvostid.matching.service.MatchScoreService;

@RestController
@RequestMapping("/api/v1/match/score")
@Tag(name = "Compatibility score")
public class MatchScoreController {
    private static final Logger log = LoggerFactory.getLogger(MatchScoreController.class);

    private final MatchScoreService matchScoreService;

    public MatchScoreController(MatchScoreService matchScoreService) {
        this.matchScoreService = matchScoreService;
    }

    @Operation(
            summary = "Calculate compatibility score",
            description =
                    "Computes compatibility between the authenticated buyer's questionnaire and the pet in the given listing.")
    @ApiResponse(
            responseCode = "200",
            description = "Score calculated",
            content = @Content(schema = @Schema(implementation = MatchScoreResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Questionnaire or listing not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Listing service unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<MatchScoreResponse> calculateScore(
            @Valid @RequestBody MatchScoreRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            HttpServletRequest httpRequest) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        String requestId = httpRequest.getHeader(SecurityHeaders.REQUEST_ID);
        log.debug("POST /api/v1/match/score, userId={}, listingId={}", userId, request.listingId());

        MatchScoreResponse response = matchScoreService.calculateScore(request.listingId(), userId, requestId);
        return ResponseEntity.ok(response);
    }
}
