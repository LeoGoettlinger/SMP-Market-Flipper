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
        if (minimumComparableSales < 1 || maximumSaleAge.isNegative() || maximumSaleAge.isZero()) {
            return Optional.empty();
        }

        boolean hasFreshSaleEvidence = evidence.comparableSales() >= minimumComparableSales
                && evidence.mostRecentSaleAt().filter(soldAt -> !soldAt.isAfter(now))
                .filter(soldAt -> Duration.between(soldAt, now).compareTo(maximumSaleAge) <= 0)
                .isPresent();
        if (hasFreshSaleEvidence) {
            Optional<BigDecimal> anchor = evidence.weightedMedianSalePrice()
                    .or(() -> evidence.lowerQuartileSalePrice());
            if (anchor.isPresent()) {
                BigDecimal fairAnchor = anchor.orElseThrow();
                BigDecimal candidate = evidence.lowerQuartileSalePrice()
                        .orElse(fairAnchor)
                        .min(fairAnchor);

                if (evidence.activeListings() >= 2 && evidence.distinctActiveSellers() >= 2
                        && evidence.lowestActiveAsk().isPresent()) {
                    BigDecimal ask = evidence.lowestActiveAsk().orElseThrow();
                    BigDecimal ratio = ask.divide(fairAnchor, 8, RoundingMode.HALF_UP);
                    if (ratio.compareTo(MINIMUM_ASK_TO_SALE_RATIO) < 0
                            || ratio.compareTo(MAXIMUM_ASK_TO_SALE_RATIO) > 0) {
                        return Optional.empty();
                    }
                    candidate = candidate.min(ask.subtract(ONE));
                }
                return positiveWholeCurrency(candidate);
            }
        }

        // No API key means no authoritative completed-sale feed. A conservative ask-only
        // estimate is permitted only when the current snapshot has breadth and seller diversity.
        if (evidence.activeListings() < 5 || evidence.distinctActiveSellers() < 3
                || evidence.lowestActiveAsk().isEmpty() || evidence.medianActiveAsk().isEmpty()) {
            return Optional.empty();
        }
        BigDecimal lowestAsk = evidence.lowestActiveAsk().orElseThrow();
        BigDecimal medianAsk = evidence.medianActiveAsk().orElseThrow();
        BigDecimal lowestToMedian = lowestAsk.divide(medianAsk, 8, RoundingMode.HALF_UP);
        if (lowestToMedian.compareTo(new BigDecimal("0.70")) < 0
                || lowestToMedian.compareTo(ONE) > 0) {
            return Optional.empty();
        }
        return positiveWholeCurrency(lowestAsk.subtract(ONE));
    }

    private Optional<BigDecimal> positiveWholeCurrency(BigDecimal candidate) {
        BigDecimal wholeCurrency = candidate.setScale(0, RoundingMode.DOWN);
        return wholeCurrency.signum() > 0 ? Optional.of(wholeCurrency) : Optional.empty();
    }
}
