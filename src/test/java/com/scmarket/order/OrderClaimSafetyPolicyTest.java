package com.scmarket.order;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderClaimSafetyPolicyTest {
    private final OrderClaimSafetyPolicy policy = new OrderClaimSafetyPolicy();
    private final Instant observedAt = Instant.parse("2026-10-24T17:54:00Z");

    @Test
    void blocksScreenshotCaseWhenOnly1724Of2048ItemsAreClaimable() {
        OrderClaimSnapshot snapshot = new OrderClaimSnapshot(
                "order-1", "minecraft:jungle_log", 2_048, 2_048, 1_724,
                new BigDecimal("413696"), new BigDecimal("413696"), observedAt);

        OrderClaimSafetyPolicy.Assessment result = policy.assessForAutomaticCollection(snapshot);

        assertFalse(result.eligible());
        assertEquals(OrderClaimSafetyPolicy.BlockReason.CLAIMABLE_QUANTITY_MISMATCH, result.reason());
    }

    @Test
    void allowsOnlyWhenFillClaimableAmountAndPayoutAllMatch() {
        OrderClaimSnapshot snapshot = new OrderClaimSnapshot(
                "order-2", "minecraft:jungle_log", 2_048, 2_048, 2_048,
                new BigDecimal("413696"), new BigDecimal("413696"), observedAt);

        OrderClaimSafetyPolicy.Assessment result = policy.assessForAutomaticCollection(snapshot);

        assertTrue(result.eligible());
        assertEquals(OrderClaimSafetyPolicy.BlockReason.NONE, result.reason());
    }

    @Test
    void blocksIncompleteOrdersBeforeConsideringPayout() {
        OrderClaimSnapshot snapshot = new OrderClaimSnapshot(
                "order-3", "minecraft:jungle_log", 2_048, 2_047, 2_047,
                new BigDecimal("413494"), new BigDecimal("413696"), observedAt);

        OrderClaimSafetyPolicy.Assessment result = policy.assessForAutomaticCollection(snapshot);

        assertFalse(result.eligible());
        assertEquals(OrderClaimSafetyPolicy.BlockReason.ORDER_NOT_FULLY_DELIVERED, result.reason());
    }

    @Test
    void blocksPayoutMismatchEvenWhenTheWholeOrderIsClaimable() {
        OrderClaimSnapshot snapshot = new OrderClaimSnapshot(
                "order-4", "minecraft:jungle_log", 2_048, 2_048, 2_048,
                new BigDecimal("413695"), new BigDecimal("413696"), observedAt);

        OrderClaimSafetyPolicy.Assessment result = policy.assessForAutomaticCollection(snapshot);

        assertFalse(result.eligible());
        assertEquals(OrderClaimSafetyPolicy.BlockReason.PAYOUT_MISMATCH, result.reason());
    }
}
