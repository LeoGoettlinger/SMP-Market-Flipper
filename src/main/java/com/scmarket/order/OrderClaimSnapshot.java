package com.scmarket.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Read-only snapshot of order fulfillment versus the amount the server currently allows to collect. */
public record OrderClaimSnapshot(
        String orderId,
        String itemId,
        int totalQuantity,
        int deliveredQuantity,
        int claimableQuantity,
        BigDecimal paidAmount,
        BigDecimal expectedPayout,
        Instant observedAt
) {
    public OrderClaimSnapshot {
        orderId = required(orderId, "orderId");
        itemId = required(itemId, "itemId");
        Objects.requireNonNull(paidAmount, "paidAmount");
        Objects.requireNonNull(expectedPayout, "expectedPayout");
        Objects.requireNonNull(observedAt, "observedAt");
        if (totalQuantity < 1 || totalQuantity > 2_048
                || deliveredQuantity < 0 || deliveredQuantity > totalQuantity
                || claimableQuantity < 0 || claimableQuantity > deliveredQuantity
                || paidAmount.signum() < 0 || expectedPayout.signum() <= 0) {
            throw new IllegalArgumentException("Order claim snapshot contains out-of-range values");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 128 || normalized.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(field + " must be bounded and nonblank");
        }
        return normalized;
    }
}
