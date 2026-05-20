package ru.hvostid.passport.entity;

/**
 * Value object stored under a media ticket key in Redis. The ticket itself is
 * an opaque random string; this record holds the bindings the content endpoint
 * verifies on redemption.
 *
 * <p>passportId is included so a leaked ticket cannot be replayed against a
 * different passport's document with the same numeric id.
 *
 * <p>storagePath and type are snapshotted at issue time so the redemption
 * path can build the X-Accel-Redirect response without a second DB lookup.
 * If the underlying document is deleted between issue and redeem, MinIO
 * returns 404 on the internal subrequest, which is the same UX as a missing
 * document.
 */
public record MediaTicket(
        long documentId, long passportId, long issuedBy, String storagePath, PassportDocumentType type) {}
