package com.scmarket.order;

import java.util.Objects;

public record OrderCycleResult(boolean actionAttempted, boolean succeeded, String message) {
    public OrderCycleResult {
        message = Objects.requireNonNullElse(message, "");
    }

    public static OrderCycleResult idle(String message) {
        return new OrderCycleResult(false, true, message);
    }

    public static OrderCycleResult failed(String message) {
        return new OrderCycleResult(true, false, message);
    }
}
