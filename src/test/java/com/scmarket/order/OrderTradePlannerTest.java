package com.scmarket.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTradePlannerTest {
    private final OrderTradePlanner planner = new OrderTradePlanner();
    private final Instant now = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void pricesOneCurrencyAboveOrderBidAndOneBelowComparableAhStack() {
        OrderTradePlan plan = planner.plan(quote("64", "1024", "4096"), now).orElseThrow();

        assertEquals(1_024, plan.orderQuantity());
        assertEquals(new BigDecimal("11"), plan.orderPricePerItem());
        assertEquals(new BigDecimal("11264"), plan.orderDeposit());
        assertEquals(new BigDecimal("113"), plan.creationFee());
        assertEquals(new BigDecimal("11377"), plan.totalOrderPayment());
        assertEquals(new BigDecimal("899"), plan.firstAhStackPrice());
        assertEquals(0, plan.expectedProfitPerItem().compareTo(new BigDecimal("2.93652343")));
        assertEquals(0, plan.expectedNetProfit().compareTo(new BigDecimal("3007")));
    }

    @Test
    void roundsOnePercentCreationFeeUpLikeTheObservedConfirmationGui() {
        assertEquals(new BigDecimal("1"), planner.estimateCreationFee(new BigDecimal("9")));
        assertEquals(new BigDecimal("31"), planner.estimateCreationFee(new BigDecimal("3072")));
        assertEquals(new BigDecimal("0"), planner.estimateCreationFee(BigDecimal.ZERO));
    }

    @Test
    void requiresAtLeastOneDollarPerItemAfterTheOrderCreationFee() {
        assertTrue(planner.plan(quote("64", "1024", "4096"), now).isPresent());
        OrderMarketQuote unprofitable = new OrderMarketQuote(
                "minecraft:stone", "Stone", new BigDecimal("10"), new BigDecimal("13"), 64,
                new BigDecimal("1024"), new BigDecimal("4096"), 4, 10, 3, now
        );
        assertTrue(planner.plan(unprofitable, now).isEmpty());
    }

    @Test
    void rejectsStaleThinAndOverFiveDayFillEvidence() {
        assertTrue(planner.plan(quote("64", "1024", "4096"), now.plusSeconds(121)).isEmpty());
        OrderMarketQuote thin = new OrderMarketQuote(
                "minecraft:stone", "Stone", new BigDecimal("10"), new BigDecimal("900"), 64,
                new BigDecimal("1024"), new BigDecimal("4096"), 2, 10, 3, now
        );
        assertTrue(planner.plan(thin, now).isEmpty());
        OrderMarketQuote slowFill = new OrderMarketQuote(
                "minecraft:stone", "Stone", new BigDecimal("10"), new BigDecimal("900"), 64,
                new BigDecimal("1"), new BigDecimal("4096"), 4, 10, 3, now
        );
        assertTrue(planner.plan(slowFill, now).isEmpty());
    }

    @Test
    void recomputesTheExactUndercutForEveryFreshStack() {
        assertEquals(new BigDecimal("99"), planner.ahStackListingPrice(new BigDecimal("100")).orElseThrow());
        assertFalse(planner.ahStackListingPrice(BigDecimal.ONE).isPresent());
    }

    private OrderMarketQuote quote(String stackSize, String fillRate, String saleRate) {
        return new OrderMarketQuote(
                "minecraft:diamond", "Diamond", new BigDecimal("10"), new BigDecimal("900"),
                Integer.parseInt(stackSize), new BigDecimal(fillRate), new BigDecimal(saleRate),
                4, 10, 3, now
        );
    }
}
