package com.scmarket.order;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/** Immutable, all-in economics for one configured Order→AH trade (default size: 1,024 items). */
public record OrderTradePlan(
        String itemId,
        String itemName,
        int orderQuantity,
        BigDecimal orderPricePerItem,
        BigDecimal orderDeposit,
        BigDecimal creationFee,
        BigDecimal totalOrderPayment,
        BigDecimal firstAhStackPrice,
        int comparableStackSize,
        BigDecimal expectedAhRevenue,
        BigDecimal expectedNetProfit,
        BigDecimal expectedProfitPerItem,
        Duration expectedFillTime,
        Duration expectedAhSellTime,
        BigDecimal expectedProfitPerHour,
        OrderMarketQuote sourceQuote
) {
    public OrderTradePlan {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(orderPricePerItem, "orderPricePerItem");
        Objects.requireNonNull(orderDeposit, "orderDeposit");
        Objects.requireNonNull(creationFee, "creationFee");
        Objects.requireNonNull(totalOrderPayment, "totalOrderPayment");
        Objects.requireNonNull(firstAhStackPrice, "firstAhStackPrice");
        Objects.requireNonNull(expectedAhRevenue, "expectedAhRevenue");
        Objects.requireNonNull(expectedNetProfit, "expectedNetProfit");
        Objects.requireNonNull(expectedProfitPerItem, "expectedProfitPerItem");
        Objects.requireNonNull(expectedFillTime, "expectedFillTime");
        Objects.requireNonNull(expectedAhSellTime, "expectedAhSellTime");
        Objects.requireNonNull(expectedProfitPerHour, "expectedProfitPerHour");
        Objects.requireNonNull(sourceQuote, "sourceQuote");
        if (orderQuantity < 1 || orderQuantity > 2_048 || comparableStackSize < 1
                || comparableStackSize > 64 || expectedNetProfit.signum() <= 0
                || expectedProfitPerItem.signum() <= 0 || expectedProfitPerHour.signum() <= 0) {
            throw new IllegalArgumentException("Order plan violates supported quantity or profitability invariants");
        }
    }
}
