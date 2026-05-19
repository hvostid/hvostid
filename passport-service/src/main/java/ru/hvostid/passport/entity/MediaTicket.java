package ru.hvostid.passport.entity;

/**
 * Value object stored under a media ticket key in Redis. The ticket itself is
 * an opaque random string; this record holds the bindings the content endpoint
 * verifies on redemption.
 *
 * <p>passportId is included so a leaked ticket cannot be replayed against a
 * different passport's document with the same numeric id.
 */
public record MediaTicket(long documentId, long passportId, long issuedBy) {}
