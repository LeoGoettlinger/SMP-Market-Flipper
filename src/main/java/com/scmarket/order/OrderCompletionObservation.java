package com.scmarket.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** One price-history fact per fully filled order, never one fact per partial delivery. */
public record OrderCompletionObservation(
        String orderId,
        String itemId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalValue,
        Instant completedAt
) {
    public OrderCompletionObservation {
        orderId = required(orderId, "orderId");
        itemId = required(itemId, "itemId");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(totalValue, "totalValue");
        Objects.requireNonNull(completedAt, "completedAt");
        if (quantity < 1 || quantity > 2_048
                || unitPrice.signum() <= 0 || totalValue.signum() <= 0) {
            throw new IllegalArgumentException("Completion quantity must be between 1 and 2048 items");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        String result = value.strip();
        if (result.isEmpty() || result.length() > 128) {
            throw new IllegalArgumentException(name + " must be bounded and nonblank");
        }
        return result;
    }
}
