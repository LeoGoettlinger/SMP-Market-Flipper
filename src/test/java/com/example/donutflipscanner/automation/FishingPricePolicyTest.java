package com.example.donutflipscanner.automation;

import com.example.donutflipscanner.automation.model.FishingMarketEvidence;
import com.example.donutflipscanner.automation.service.FishingPricePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingPricePolicyTest {
    private final FishingPricePolicy policy = new FishingPricePolicy();
    private final Instant now = Instant.parse("2026-10-19T10:00:00Z");

    @Test
    void usesConservativeRecentSalesPriceWithoutReliableAsks() {
        var evidence = new FishingMarketEvidence(10, money("90.8"), money("100"), 1, 1,
                money("110"), Optional.of(now.minus(Duration.ofHours(1))), now);

        assertEquals(money("90"), policy.listingPrice(evidence, 8, now, Duration.ofHours(24)));
    }

    @Test
    void undercutsMultipleCredibleActiveSellersButStaysConservative() {
        var evidence = new FishingMarketEvidence(10, money("95"), money("100"), 4, 3,
                money("98"), Optional.of(now.minus(Duration.ofHours(1))), now);

        assertEquals(money("95"), policy.listingPrice(evidence, 8, now, Duration.ofHours(24)));
    }

    @Test
    void refusesInsufficientOrStaleEvidenceAndSuspiciousAsk() {
        var tooFewSales = evidence(2, Duration.ofHours(1), money("100"));
        var stale = evidence(10, Duration.ofDays(2), money("100"));
        var suspicious = new FishingMarketEvidence(10, money("95"), money("100"), 4, 3,
                money("20"), Optional.of(now.minus(Duration.ofHours(1))), now);

        assertTrue(policy.listingPrice(tooFewSales, 8, now, Duration.ofHours(24)).isEmpty());
        assertTrue(policy.listingPrice(stale, 8, now, Duration.ofHours(24)).isEmpty());
        assertTrue(policy.listingPrice(suspicious, 8, now, Duration.ofHours(24)).isEmpty());
    }

    private FishingMarketEvidence evidence(int sales, Duration age, Optional<BigDecimal> ask) {
        return new FishingMarketEvidence(sales, money("95"), money("100"), 4, 3, ask,
                Optional.of(now.minus(age)), now);
    }

    private static Optional<BigDecimal> money(String value) {
        return Optional.of(new BigDecimal(value));
    }
}
