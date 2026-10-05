package ru.hvostid.passport.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.common.dto.ErrorResponse;
import ru.hvostid.passport.dto.PassportResponse;
import ru.hvostid.passport.service.PassportService;

@Hidden
@RestController
@RequestMapping("/internal/passports")
public class InternalPassportController {
    private static final Logger log = LoggerFactory.getLogger(InternalPassportController.class);

    private final PassportService passportService;
    private final ru.hvostid.passport.service.PassportReferenceService references;

    public InternalPassportController(
            PassportService passportService, ru.hvostid.passport.service.PassportReferenceService references) {
        this.passportService = passportService;
        this.references = references;
    }

    @Operation(
            summary = "Internal passport read (service-to-service)",
            description = "Returns passport data for compatibility scoring. Not routed through the Gateway.")
    @ApiResponse(
            responseCode = "200",
            description = "Passport found",
            content = @Content(schema = @Schema(implementation = PassportResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Passport not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{petId}")
    public ResponseEntity<PassportResponse> getPassport(
            @Parameter(description = "Pet passport ID", required = true, example = "1") @PathVariable Long petId) {
        log.debug("GET /internal/passports/{}", petId);
        PassportResponse response = passportService.getPassportForInternal(petId);
        return ResponseEntity.ok(response);
    }

    public record ReferenceRequest(
            @jakarta.validation.constraints.Positive long sellerId,
            boolean approved,
            @jakarta.validation.constraints.Positive long revision) {}

    @org.springframework.web.bind.annotation.PutMapping("/{petId}/references/{listingId}")
    public ResponseEntity<Void> acquire(
            @PathVariable Long petId,
            @PathVariable Long listingId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody ReferenceRequest request) {
        references.acquire(petId, listingId, request.sellerId(), request.approved(), request.revision());
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{petId}/references/{listingId}")
    public ResponseEntity<Void> release(
            @PathVariable Long petId,
            @PathVariable Long listingId,
            @org.springframework.web.bind.annotation.RequestParam @jakarta.validation.constraints.Positive
                    long revision) {
        references.release(petId, listingId, revision);
        return ResponseEntity.noContent().build();
    }
}
