package com.scmarket.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Independent, timestamped observations from the Order book and AH sales market. */
public record OrderMarketQuote(
        String itemId,
        String itemName,
        BigDecimal competingTopBidPerItem,
        BigDecimal lowestComparableStackPrice,
        int comparableStackSize,
        BigDecimal observedOrderFillItemsPerHour,
        BigDecimal observedAhSaleItemsPerHour,
        int orderEvidenceCount,
        int ahSaleEvidenceCount,
        int distinctAhSellers,
        Instant observedAt
) {
    public OrderMarketQuote {
        itemId = bounded(itemId, "itemId", 128);
        itemName = bounded(itemName, "itemName", 128);
        competingTopBidPerItem = positive(competingTopBidPerItem, "competingTopBidPerItem");
        lowestComparableStackPrice = positive(lowestComparableStackPrice, "lowestComparableStackPrice");
        observedOrderFillItemsPerHour = positive(observedOrderFillItemsPerHour,
                "observedOrderFillItemsPerHour");
        observedAhSaleItemsPerHour = positive(observedAhSaleItemsPerHour, "observedAhSaleItemsPerHour");
        Objects.requireNonNull(observedAt, "observedAt");
        if (comparableStackSize < 1 || comparableStackSize > 64
                || orderEvidenceCount < 1 || ahSaleEvidenceCount < 1 || distinctAhSellers < 1) {
            throw new IllegalArgumentException("Order quote evidence and stack size are invalid");
        }
    }

    private static String bounded(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        String result = value.strip();
        if (result.isEmpty() || result.length() > maximumLength || result.indexOf('\n') >= 0
                || result.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must be a bounded single-line value");
        }
        return result;
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
