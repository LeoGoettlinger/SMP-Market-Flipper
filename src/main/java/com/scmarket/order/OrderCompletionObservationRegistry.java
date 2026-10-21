package com.scmarket.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Session-level deduplicator: partial deliveries and repeated GUI reads never become separate facts. */
public final class OrderCompletionObservationRegistry {
    private final Set<String> recordedOrderIds = new HashSet<>();

    public synchronized Optional<OrderCompletionObservation> observe(
            TrackedOrder order, BigDecimal pricePerItem, Instant completedAt
    ) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(pricePerItem, "pricePerItem");
        Objects.requireNonNull(completedAt, "completedAt");
        if (order.state() != TrackedOrderState.FILLED_UNCLAIMED
                || order.filledQuantity() != order.totalQuantity()
                || recordedOrderIds.contains(order.orderId())) {
            return Optional.empty();
        }
        OrderCompletionObservation observation = new OrderCompletionObservation(
                order.orderId(), order.itemId(), order.totalQuantity(),
                pricePerItem, pricePerItem.multiply(BigDecimal.valueOf(order.totalQuantity())),
                completedAt
        );
        recordedOrderIds.add(order.orderId());
        return Optional.of(observation);
    }

    public synchronized void markRecorded(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        if (orderId.isBlank() || orderId.length() > 128) {
            throw new IllegalArgumentException("orderId must be bounded and nonblank");
        }
        recordedOrderIds.add(orderId);
    }
}
