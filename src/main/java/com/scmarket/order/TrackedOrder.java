package com.scmarket.order;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record TrackedOrder(
        String orderId,
        String itemId,
        String itemName,
        int totalQuantity,
        int filledQuantity,
        java.math.BigDecimal pricePerItem,
        TrackedOrderState state,
        Instant createdAt,
        Instant earliestDeliveryAt,
        Optional<OrderClaimSnapshot> claimSnapshot
) {
    /** Compatibility constructor: claim details are unknown and therefore cannot authorize collection. */
    public TrackedOrder(
            String orderId,
            String itemId,
            String itemName,
            int totalQuantity,
            int filledQuantity,
            java.math.BigDecimal pricePerItem,
            TrackedOrderState state,
            Instant createdAt,
            Instant earliestDeliveryAt
    ) {
        this(orderId, itemId, itemName, totalQuantity, filledQuantity, pricePerItem, state,
                createdAt, earliestDeliveryAt, Optional.empty());
    }

    public TrackedOrder {
        orderId = required(orderId, "orderId");
        itemId = required(itemId, "itemId");
        itemName = required(itemName, "itemName");
        Objects.requireNonNull(pricePerItem, "pricePerItem");
        if (pricePerItem.signum() <= 0) {
            throw new IllegalArgumentException("pricePerItem must be positive");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        earliestDeliveryAt = Objects.requireNonNullElse(earliestDeliveryAt, createdAt);
        claimSnapshot = Objects.requireNonNull(claimSnapshot, "claimSnapshot");
        if (totalQuantity < 1 || totalQuantity > 2_048
                || filledQuantity < 0 || filledQuantity > totalQuantity) {
            throw new IllegalArgumentException("Tracked order quantity must be between 1 and 2048 items");
        }
        if (state == TrackedOrderState.FILLED_UNCLAIMED && filledQuantity != totalQuantity) {
            throw new IllegalArgumentException("An unclaimed completed order must be fully filled");
        }
        if (claimSnapshot.isPresent()) {
            OrderClaimSnapshot snapshot = claimSnapshot.orElseThrow();
            if (!snapshot.orderId().equals(orderId)
                    || !snapshot.itemId().equals(itemId)
                    || snapshot.totalQuantity() != totalQuantity
                    || snapshot.deliveredQuantity() != filledQuantity
                    || snapshot.expectedPayout().compareTo(
                            pricePerItem.multiply(java.math.BigDecimal.valueOf(totalQuantity))) != 0) {
                throw new IllegalArgumentException("Claim snapshot does not match its tracked order");
            }
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        String result = value.strip();
        if (result.isEmpty() || result.length() > 128 || result.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " must be a bounded, nonblank value");
        }
        return result;
    }
}
