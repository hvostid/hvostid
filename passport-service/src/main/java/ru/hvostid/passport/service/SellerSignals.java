package ru.hvostid.passport.service;

/**
 * External signals about the seller used by the trust-score calculator.
 * Rating and salesCount may be null when the source is unavailable.
 */
public record SellerSignals(Double rating, Integer salesCount) {
    public static SellerSignals empty() {
        return new SellerSignals(null, null);
    }
}
