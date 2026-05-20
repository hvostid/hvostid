package ru.hvostid.passport.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.passport.entity.MediaTicket;
import ru.hvostid.passport.service.MediaStreamRedirectFactory;
import ru.hvostid.passport.service.MediaTicketService;

/**
 * Streams passport document bytes via the X-Accel-Redirect handoff to the
 * frontend nginx.
 *
 * <p>This endpoint MUST be listed in {@code hvostid.auth.public-paths} on
 * the gateway so the introspection filter lets the request through. Access
 * is enforced by the single use ticket, not by Bearer auth, which is what
 * lets it work with {@code <img src>}.
 *
 * <p>Response shape: {@code 204 No Content} with two headers. X-Accel-Redirect
 * points nginx at an internal location that proxies to MinIO with a short
 * lived presigned URL. Referrer-Policy keeps the ticketed URL out of the
 * Referer header when the image is embedded on third party pages.
 *
 * <p>Status codes the client may see (from the subrequest):
 * 200 with bytes, 404 if the ticket is unknown/redeemed/expired or does not
 * match the path.
 */
@RestController
@RequestMapping("/api/v1/passports/{passportId}/docs/{docId}/content")
@Tag(name = "Passport documents")
public class PassportMediaController {
    private static final Logger log = LoggerFactory.getLogger(PassportMediaController.class);

    private final MediaTicketService ticketService;
    private final MediaStreamRedirectFactory redirectFactory;

    public PassportMediaController(MediaTicketService ticketService, MediaStreamRedirectFactory redirectFactory) {
        this.ticketService = ticketService;
        this.redirectFactory = redirectFactory;
    }

    @Operation(
            summary = "Stream a passport document via X-Accel-Redirect",
            description =
                    "Validates a single use ticket and hands off bytes to the frontend nginx. Public on the gateway; access is enforced by the ticket.")
    @ApiResponse(responseCode = "204", description = "Nginx will serve the bytes via internal redirect.")
    @ApiResponse(
            responseCode = "404",
            description = "Unknown, redeemed, expired, or mismatched ticket.",
            content = @Content)
    @GetMapping
    public ResponseEntity<Void> stream(
            @Parameter(description = "Pet passport ID", required = true) @PathVariable Long passportId,
            @Parameter(description = "Passport document ID", required = true) @PathVariable Long docId,
            @Parameter(description = "Single use ticket", required = true) @RequestParam("t") String token) {

        Optional<MediaTicket> ticketOpt = ticketService.redeem(token);
        if (ticketOpt.isEmpty()) {
            log.debug("Media ticket missing or already redeemed");
            return notFound();
        }

        MediaTicket ticket = ticketOpt.get();
        if (ticket.documentId() != docId || ticket.passportId() != passportId) {
            log.warn(
                    "Media ticket path mismatch pathDocId={} ticketDocId={} pathPassportId={} ticketPassportId={}",
                    docId,
                    ticket.documentId(),
                    passportId,
                    ticket.passportId());
            return notFound();
        }

        log.debug("Streaming media docId={} via X-Accel-Redirect", docId);
        return redirectFactory.noContentXAccelRedirect(ticket.storagePath(), ticket.type());
    }

    private ResponseEntity<Void> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
