package ru.hvostid.matching.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.matching.dto.QuestionnaireRequest;
import ru.hvostid.matching.dto.QuestionnaireResponse;
import ru.hvostid.matching.exception.GlobalExceptionHandler;
import ru.hvostid.matching.service.QuestionnaireService;

@RestController
@RequestMapping("/api/v1/match/questionnaire")
@Tag(name = "Buyer questionnaire")
public class QuestionnaireController {
    private static final Logger log = LoggerFactory.getLogger(QuestionnaireController.class);

    private final QuestionnaireService questionnaireService;

    public QuestionnaireController(QuestionnaireService questionnaireService) {
        this.questionnaireService = questionnaireService;
    }

    @Operation(
            summary = "Save or update buyer questionnaire",
            description =
                    "Creates a new questionnaire for the authenticated user, or updates the existing one if it already exists. Idempotent.")
    @ApiResponse(
            responseCode = "200",
            description = "Questionnaire saved",
            content = @Content(schema = @Schema(implementation = QuestionnaireResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @PostMapping
    public ResponseEntity<QuestionnaireResponse> upsertQuestionnaire(
            @Valid @RequestBody QuestionnaireRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        log.debug("POST /api/v1/match/questionnaire, userId={}", userId);

        QuestionnaireResponse response = questionnaireService.upsertQuestionnaire(request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get the authenticated user's questionnaire",
            description = "Returns the buyer questionnaire owned by the authenticated user.")
    @ApiResponse(
            responseCode = "200",
            description = "Questionnaire found",
            content = @Content(schema = @Schema(implementation = QuestionnaireResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Questionnaire not yet created for this user",
            content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    @GetMapping
    public ResponseEntity<QuestionnaireResponse> getQuestionnaire(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        log.debug("GET /api/v1/match/questionnaire, userId={}", userId);

        QuestionnaireResponse response = questionnaireService.getQuestionnaire(userId);
        return ResponseEntity.ok(response);
    }
}
