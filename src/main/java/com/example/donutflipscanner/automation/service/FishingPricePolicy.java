package com.example.donutflipscanner.automation.service;

import com.example.donutflipscanner.automation.model.FishingMarketEvidence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Fail-closed listing-price policy: it never fabricates a price from missing or stale evidence. */
public final class FishingPricePolicy {
    private static final BigDecimal MINIMUM_ASK_TO_SALE_RATIO = new BigDecimal("0.70");
    private static final BigDecimal MAXIMUM_ASK_TO_SALE_RATIO = new BigDecimal("1.50");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    public Optional<BigDecimal> listingPrice(
            FishingMarketEvidence evidence,
            int minimumComparableSales,
            Instant now,
            Duration maximumSaleAge
    ) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(maximumSaleAge, "maximumSaleAge");
        if (minimumComparableSales < 1 || maximumSaleAge.isNegative() || maximumSaleAge.isZero()
                || evidence.comparableSales() < minimumComparableSales
                || evidence.mostRecentSaleAt().isEmpty()
                || evidence.mostRecentSaleAt().orElseThrow().isAfter(now)
                || Duration.between(evidence.mostRecentSaleAt().orElseThrow(), now).compareTo(maximumSaleAge) > 0) {
            return Optional.empty();
        }

        Optional<BigDecimal> anchor = evidence.weightedMedianSalePrice()
                .or(() -> evidence.lowerQuartileSalePrice());
        if (anchor.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal fairAnchor = anchor.orElseThrow();
        BigDecimal conservative = evidence.lowerQuartileSalePrice()
                .orElse(fairAnchor)
                .min(fairAnchor);

        BigDecimal candidate = conservative;
        if (evidence.activeListings() >= 2 && evidence.distinctActiveSellers() >= 2
                && evidence.lowestActiveAsk().isPresent()) {
            BigDecimal ask = evidence.lowestActiveAsk().orElseThrow();
            BigDecimal ratio = ask.divide(fairAnchor, 8, RoundingMode.HALF_UP);
            if (ratio.compareTo(MINIMUM_ASK_TO_SALE_RATIO) < 0
                    || ratio.compareTo(MAXIMUM_ASK_TO_SALE_RATIO) > 0) {
                return Optional.empty();
            }
            BigDecimal undercut = ask.subtract(ONE);
            candidate = candidate.min(undercut);
        }

        BigDecimal wholeCurrency = candidate.setScale(0, RoundingMode.DOWN);
        return wholeCurrency.signum() > 0 && wholeCurrency.compareTo(ZERO) > 0
                ? Optional.of(wholeCurrency) : Optional.empty();
    }
}
