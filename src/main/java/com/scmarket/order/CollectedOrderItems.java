package com.scmarket.order;

import java.util.List;
import java.util.Objects;

/** Verified inventory delta for one complete order; never includes unrelated pre-existing inventory. */
public record CollectedOrderItems(String orderId, String itemId, int expectedQuantity, List<OrderSaleStack> stacks) {
    public CollectedOrderItems(String orderId, String itemId, List<OrderSaleStack> stacks) {
        this(orderId, itemId, OrderTradePlanner.ORDER_QUANTITY, stacks);
    }

    public CollectedOrderItems {
        orderId = required(orderId, "orderId");
        itemId = required(itemId, "itemId");
        stacks = List.copyOf(Objects.requireNonNull(stacks, "stacks"));
        String expectedOrderId = orderId;
        String expectedItemId = itemId;
        if (expectedQuantity < 1 || expectedQuantity > 2_048 || stacks.isEmpty()
                || stacks.stream().anyMatch(stack -> !stack.orderId().equals(expectedOrderId)
                        || !stack.itemId().equals(expectedItemId))
                || stacks.stream().mapToInt(OrderSaleStack::count).sum() != expectedQuantity) {
            throw new IllegalArgumentException("Collected inventory must exactly match one complete order");
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
