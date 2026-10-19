package com.example.donutflipscanner.automation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Bounded market evidence used to decide whether a caught item may be listed. */
public record FishingMarketEvidence(
        int comparableSales,
        Optional<BigDecimal> lowerQuartileSalePrice,
        Optional<BigDecimal> weightedMedianSalePrice,
        int activeListings,
        int distinctActiveSellers,
        Optional<BigDecimal> lowestActiveAsk,
        Optional<Instant> mostRecentSaleAt,
        Instant observedAt
) {
    public FishingMarketEvidence {
        if (comparableSales < 0 || activeListings < 0 || distinctActiveSellers < 0) {
            throw new IllegalArgumentException("market evidence counts must not be negative");
        }
        lowerQuartileSalePrice = positive(lowerQuartileSalePrice);
        weightedMedianSalePrice = positive(weightedMedianSalePrice);
        lowestActiveAsk = positive(lowestActiveAsk);
        mostRecentSaleAt = Objects.requireNonNullElse(mostRecentSaleAt, Optional.empty());
        Objects.requireNonNull(observedAt, "observedAt");
    }

    private static Optional<BigDecimal> positive(Optional<BigDecimal> value) {
        Optional<BigDecimal> safe = Objects.requireNonNullElse(value, Optional.empty());
        if (safe.stream().anyMatch(price -> price.signum() <= 0)) {
            throw new IllegalArgumentException("market prices must be positive");
        }
        return safe;
    }
}
