package com.scmarket.order;

import com.scmarket.configuration.OrderModuleSettings;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCapacityPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private final OrderCapacityPolicy policy = new OrderCapacityPolicy();

    @Test
    void includesFilledButUnclaimedOrdersInBothLimits() {
        TrackedOrder filledUnclaimed = order("one", "minecraft:diamond", TrackedOrderState.FILLED_UNCLAIMED);
        TrackedOrder open = order("two", "minecraft:diamond", TrackedOrderState.OPEN);
        assertFalse(policy.canPlace(List.of(filledUnclaimed, open), "minecraft:diamond").allowed());
        assertTrue(policy.canPlace(List.of(filledUnclaimed, open), "minecraft:emerald").allowed());
    }

    @Test
    void userCanLowerTheServerCapacityCeilingWithoutExceedingIt() {
        OrderModuleSettings settings = new OrderModuleSettings(java.util.Set.of(), 1_024,
                1, 1, java.math.BigDecimal.ONE, false, 5_000);
        OrderCapacityPolicy configured = new OrderCapacityPolicy(() -> settings);

        assertFalse(configured.canPlace(List.of(order("one", "minecraft:diamond",
                TrackedOrderState.OPEN)), "minecraft:emerald").allowed());
        assertFalse(configured.canPlace(List.of(order("one", "minecraft:diamond",
                TrackedOrderState.OPEN)), "minecraft:diamond").allowed());
    }

    @Test
    void limitsAllOrdersToTenAndIgnoresCollectedOrCancelledOrders() {
        List<TrackedOrder> ten = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            ten.add(order("open-" + index, "minecraft:item_" + index, TrackedOrderState.OPEN));
        }
        ten.add(order("done", "minecraft:emerald", TrackedOrderState.FILLED_UNCLAIMED));
        assertFalse(policy.canPlace(ten, "minecraft:diamond").allowed());
        assertTrue(policy.canPlace(List.of(
                order("collected", "minecraft:diamond", TrackedOrderState.COLLECTED),
                order("cancelled", "minecraft:diamond", TrackedOrderState.CANCELLED)
        ), "minecraft:diamond").allowed());
    }

    private static TrackedOrder order(String id, String itemId, TrackedOrderState state) {
        int filled = state == TrackedOrderState.FILLED_UNCLAIMED ? 1_024
                : state == TrackedOrderState.PARTIALLY_FILLED ? 512 : 0;
        return new TrackedOrder(id, itemId, itemId, 1_024, filled, new java.math.BigDecimal("10"), state, NOW, NOW);
    }
}
