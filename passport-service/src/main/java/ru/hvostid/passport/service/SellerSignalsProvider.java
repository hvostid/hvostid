package ru.hvostid.passport.service;

/**
 * Source of seller-level signals (rating, completed sales) consumed by
 * {@link TrustScoreCalculator}. The default implementation returns
 * empty signals; a future PR can wire it to auth-service / listing-service.
 */
public interface SellerSignalsProvider {
    SellerSignals fetch(long sellerId);
}
