package com.scmarket.order;

public record OrderSaleReceipt(boolean verified, int listedCount, int remainingCount, String message) {
    public OrderSaleReceipt {
        message = java.util.Objects.requireNonNullElse(message, "");
        if (listedCount < 0 || remainingCount < 0 || listedCount + remainingCount > 64) {
            throw new IllegalArgumentException("AH sale receipt counts are invalid");
        }
        if (verified && listedCount == 0 && remainingCount > 0) {
            throw new IllegalArgumentException("A verified listing must make progress");
        }
    }
}
