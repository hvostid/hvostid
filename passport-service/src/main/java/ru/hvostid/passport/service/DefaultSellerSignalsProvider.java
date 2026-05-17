package ru.hvostid.passport.service;

import org.springframework.stereotype.Component;

/**
 * Default implementation that returns no signals so the corresponding
 * trust-score components contribute 0. Replace with a bean annotated
 * {@code @Primary} once auth-service / listing-service expose seller stats.
 */
@Component
public class DefaultSellerSignalsProvider implements SellerSignalsProvider {
    @Override
    public SellerSignals fetch(long sellerId) {
        return SellerSignals.empty();
    }
}
