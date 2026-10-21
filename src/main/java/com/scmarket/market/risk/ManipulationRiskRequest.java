package com.scmarket.market.risk;

import com.scmarket.market.item.model.NormalizedItem;
import com.scmarket.market.value.FairValueMarketContext;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record ManipulationRiskRequest(
        NormalizedItem item,
        FairValueMarketContext market,
        Optional<Duration> variantKnownAge
) {
    public ManipulationRiskRequest {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(market, "market");
        variantKnownAge = Objects.requireNonNullElse(variantKnownAge, Optional.empty());
        if (variantKnownAge.isPresent() && variantKnownAge.orElseThrow().isNegative()) {
            throw new IllegalArgumentException("variantKnownAge must not be negative");
        }
        if (!item.fingerprint().sha256().equals(market.primary().itemFingerprint())) {
            throw new IllegalArgumentException("item and market fingerprints must match");
        }
    }
}
