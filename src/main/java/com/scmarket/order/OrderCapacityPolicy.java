package com.scmarket.order;

import com.scmarket.configuration.OrderModuleSettings;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

/** Enforces configurable limits while never allowing more than the server-observed 10/2 maxima. */
public final class OrderCapacityPolicy {
    public static final int MAXIMUM_TOTAL_ORDERS = OrderModuleSettings.MAXIMUM_TOTAL_ORDERS;
    public static final int MAXIMUM_ORDERS_PER_ITEM = OrderModuleSettings.MAXIMUM_ORDERS_PER_ITEM;
    private final Supplier<OrderModuleSettings> settings;

    public OrderCapacityPolicy() {
        this(OrderModuleSettings::defaults);
    }

    public OrderCapacityPolicy(Supplier<OrderModuleSettings> settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public CapacityCheck canPlace(Collection<TrackedOrder> orders, String itemId) {
        Objects.requireNonNull(orders, "orders");
        Objects.requireNonNull(itemId, "itemId");
        return canPlace(orders, itemId, Objects.requireNonNull(settings.get(), "current settings"));
    }

    public CapacityCheck canPlace(
            Collection<TrackedOrder> orders, String itemId, OrderModuleSettings limits
    ) {
        Objects.requireNonNull(orders, "orders");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(limits, "limits");
        String normalizedItemId = itemId.strip();
        if (normalizedItemId.isEmpty()) {
            return new CapacityCheck(false, 0, 0, "Item ID is missing.");
        }
        int total = 0;
        int perItem = 0;
        for (TrackedOrder order : orders) {
            if (occupiesOrderSlot(order.state())) {
                total++;
                if (order.itemId().equals(normalizedItemId)) {
                    perItem++;
                }
            }
        }
        if (total >= limits.maximumTotalOrders()) {
            return new CapacityCheck(false, total, perItem,
                    "Configured total-order limit is full, including completed orders awaiting pickup.");
        }
        if (perItem >= limits.maximumOrdersPerItem()) {
            return new CapacityCheck(false, total, perItem,
                    "Configured per-item limit is full, including completed orders awaiting pickup.");
        }
        return new CapacityCheck(true, total, perItem, "Order capacity is available.");
    }

    public static boolean occupiesOrderSlot(TrackedOrderState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case OPEN, PARTIALLY_FILLED, FILLED_UNCLAIMED -> true;
            case COLLECTED, CANCELLED, SOLD -> false;
        };
    }

    public record CapacityCheck(boolean allowed, int totalOrdersInUse, int itemOrdersInUse, String reason) {
        public CapacityCheck {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
