package com.scmarket.market.value;

import com.scmarket.market.statistics.model.ItemMarketStatistics;

import java.util.Objects;

public record FairValueMarketContext(
        ItemMarketStatistics primary,
        ItemMarketStatistics recent,
        ItemMarketStatistics longTerm
) {
    public FairValueMarketContext {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(recent, "recent");
        Objects.requireNonNull(longTerm, "longTerm");
        if (!primary.itemFingerprint().equals(recent.itemFingerprint())
                || !primary.itemFingerprint().equals(longTerm.itemFingerprint())) {
            throw new IllegalArgumentException("fair-value windows must describe the same fingerprint");
        }
    }
}
