package ru.hvostid.passport.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.hvostid.common.dto.ErrorResponse;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.passport.dto.PassportDocumentResponse;
import ru.hvostid.passport.entity.PassportDocumentType;
import ru.hvostid.passport.service.PassportDocumentService;

@RestController
@RequestMapping("/api/v1/passports/{passportId}/docs")
@Tag(name = "Passport documents")
public class PassportDocumentController {
    private static final Logger log = LoggerFactory.getLogger(PassportDocumentController.class);

    private final PassportDocumentService documentService;

    public PassportDocumentController(PassportDocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Upload a passport document", description = "Uploads a photo or document to a pet passport.")
    @ApiResponse(
            responseCode = "201",
            description = "Document uploaded successfully",
            content = @Content(schema = @Schema(implementation = PassportDocumentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid multipart request", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "User is not the passport owner", content = @Content)
    @ApiResponse(responseCode = "404", description = "Passport not found", content = @Content)
    @ApiResponse(responseCode = "413", description = "File is too large", content = @Content)
    @ApiResponse(responseCode = "415", description = "Unsupported file format", content = @Content)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole(T(ru.hvostid.common.security.UserRole).SELLER.value())")
    public ResponseEntity<PassportDocumentResponse> uploadDocument(
            @Parameter(description = "Pet passport ID", required = true, example = "1") @PathVariable Long passportId,
            @RequestPart("file") MultipartFile file,
            @RequestParam PassportDocumentType type,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        log.debug("POST /api/v1/passports/{}/docs, userId={}, type={}", passportId, userId, type);

        PassportDocumentResponse response = documentService.uploadDocument(passportId, file, type, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "List passport documents",
            description = "Returns document metadata for the owner, moderators, and admins.")
    @ApiResponse(
            responseCode = "200",
            description = "Document list",
            content = @Content(schema = @Schema(implementation = PassportDocumentResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "User is not allowed to view passport documents",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Passport not found", content = @Content)
    @GetMapping
    public ResponseEntity<List<PassportDocumentResponse>> listDocuments(
            @Parameter(description = "Pet passport ID", required = true, example = "1") @PathVariable Long passportId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        Set<String> roles = currentRoles(user);
        log.debug("GET /api/v1/passports/{}/docs, userId={}, roles={}", passportId, userId, roles);

        return ResponseEntity.ok(documentService.listDocuments(passportId, userId, roles));
    }

    @Operation(summary = "Download a passport document", description = "Redirects to a temporary MinIO presigned URL.")
    @ApiResponse(responseCode = "302", description = "Redirect to presigned URL", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "User is not allowed to view passport documents",
            content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Passport or document not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{docId}")
    public ResponseEntity<Void> downloadDocument(
            @Parameter(description = "Pet passport ID", required = true, example = "1") @PathVariable Long passportId,
            @Parameter(description = "Passport document ID", required = true, example = "1") @PathVariable Long docId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        Set<String> roles = currentRoles(user);
        log.debug("GET /api/v1/passports/{}/docs/{}, userId={}, roles={}", passportId, docId, userId, roles);

        String url = documentService.getDownloadUrl(passportId, docId, userId, roles);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @Operation(summary = "Delete a passport document", description = "Deletes a passport document owned by the seller.")
    @ApiResponse(responseCode = "204", description = "Document deleted", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing or invalid authenticated user", content = @Content)
    @ApiResponse(responseCode = "403", description = "User is not the passport owner", content = @Content)
    @ApiResponse(responseCode = "404", description = "Passport or document not found", content = @Content)
    @DeleteMapping("/{docId}")
    @PreAuthorize("hasRole(T(ru.hvostid.common.security.UserRole).SELLER.value())")
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Pet passport ID", required = true, example = "1") @PathVariable Long passportId,
            @Parameter(description = "Passport document ID", required = true, example = "1") @PathVariable Long docId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user) {
        long userId = GatewayPreAuthentication.currentUserId(user);
        log.debug("DELETE /api/v1/passports/{}/docs/{}, userId={}", passportId, docId, userId);

        documentService.deleteDocument(passportId, docId, userId);
        return ResponseEntity.noContent().build();
    }

    private Set<String> currentRoles(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth != null)
                .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                .collect(Collectors.toSet());
    }
}
