package com.scmarket.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCompletionObservationRegistryTest {
    @Test
    void recordsOneObservationForTheFullOrderAndDeduplicatesRepeatedReads() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        OrderCompletionObservationRegistry registry = new OrderCompletionObservationRegistry();
        TrackedOrder partial = new TrackedOrder("order-1", "minecraft:diamond", "Diamond",
                1_024, 512, new BigDecimal("10"), TrackedOrderState.PARTIALLY_FILLED, now, now);
        TrackedOrder complete = new TrackedOrder("order-1", "minecraft:diamond", "Diamond",
                1_024, 1_024, new BigDecimal("11"), TrackedOrderState.FILLED_UNCLAIMED, now, now);

        assertTrue(registry.observe(partial, new BigDecimal("10"), now).isEmpty());
        OrderCompletionObservation observation = registry.observe(
                complete, new BigDecimal("11"), now).orElseThrow();

        assertEquals(1_024, observation.quantity());
        assertEquals(new BigDecimal("11264"), observation.totalValue());
        assertTrue(registry.observe(complete, new BigDecimal("11"), now).isEmpty());
    }
}
