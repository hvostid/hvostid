package ru.hvostid.passport.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.common.http.SecurityHeaders;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.service.MediaStreamRedirectFactory;
import ru.hvostid.passport.service.PassportDocumentService;

/**
 * Streams the cover photo of a passport that backs at least one PUBLISHED
 * listing. Public path: catalog cards render via plain
 * {@code <img src="/api/v1/passports/{id}/cover">} without a Bearer token
 * and without a ticket round trip.
 *
 * <p>Must be listed in {@code hvostid.auth.public-paths} on the gateway and
 * permitted in {@code SecurityConfig} locally. Confidentiality is upheld by
 * the listing-service check: every passport that resolves to a cover image
 * is already discoverable through the public catalog API, so exposing the
 * cover does not leak anything the catalog does not.
 */
@RestController
@RequestMapping("/api/v1/passports/{passportId}/cover")
@Tag(name = "Passport documents")
public class PassportCoverController {
    private static final Logger log = LoggerFactory.getLogger(PassportCoverController.class);

    private final PassportDocumentService documentService;
    private final MediaStreamRedirectFactory redirectFactory;

    public PassportCoverController(
            PassportDocumentService documentService, MediaStreamRedirectFactory redirectFactory) {
        this.documentService = documentService;
        this.redirectFactory = redirectFactory;
    }

    @Operation(
            summary = "Stream the catalog cover photo for a passport",
            description = "Returns 204 + X-Accel-Redirect for the first PHOTO uploaded to a passport that backs"
                    + " at least one PUBLISHED listing. Public on the gateway; no Bearer token, no ticket.")
    @ApiResponse(responseCode = "204", description = "Nginx will serve the bytes via internal redirect.")
    @ApiResponse(
            responseCode = "404",
            description = "Passport not found, no PUBLISHED listing references it, or no PHOTO is attached.",
            content = @Content)
    @ApiResponse(responseCode = "503", description = "Listing service unavailable", content = @Content)
    @GetMapping
    public ResponseEntity<Void> stream(
            @Parameter(description = "Pet passport ID", required = true) @PathVariable Long passportId,
            HttpServletRequest httpRequest) {
        String requestId = httpRequest.getHeader(SecurityHeaders.REQUEST_ID);
        PassportDocument cover = documentService.resolveCoverPhoto(passportId, requestId);
        log.debug("Streaming cover passportId={} docId={}", passportId, cover.getId());
        return redirectFactory.noContentXAccelRedirect(cover);
    }
}
