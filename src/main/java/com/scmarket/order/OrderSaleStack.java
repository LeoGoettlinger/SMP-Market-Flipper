package com.scmarket.order;

import java.util.Objects;

/** Exact stack attribution returned after a single completed order is collected. */
public record OrderSaleStack(String orderId, String itemId, String fingerprint, int count) {
    public OrderSaleStack {
        orderId = required(orderId, "orderId");
        itemId = required(itemId, "itemId");
        fingerprint = required(fingerprint, "fingerprint");
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("A single AH listing must be one valid Minecraft stack");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        String result = value.strip();
        if (result.isEmpty() || result.length() > 256) {
            throw new IllegalArgumentException(name + " must be bounded and nonblank");
        }
        return result;
    }
}
