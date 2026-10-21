package com.scmarket.order;

import com.scmarket.configuration.OrderModuleSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderOpportunitySelectorTest {
    @Test
    void optimizesProfitRateUsingBothFillAndAhSalesVelocity() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        OrderMarketQuote highProfitSlow = new OrderMarketQuote(
                "minecraft:diamond", "Diamond", new BigDecimal("10"), new BigDecimal("1000"), 64,
                new BigDecimal("1024"), new BigDecimal("1024"), 4, 10, 3, now
        );
        OrderMarketQuote lowerProfitFast = new OrderMarketQuote(
                "minecraft:emerald", "Emerald", new BigDecimal("10"), new BigDecimal("900"), 64,
                new BigDecimal("4096"), new BigDecimal("8192"), 4, 10, 3, now
        );

        OrderTradePlan selected = new OrderOpportunitySelector().best(
                List.of(highProfitSlow, lowerProfitFast), now).orElseThrow();

        assertEquals("minecraft:emerald", selected.itemId());
    }

    @Test
    void configuredWhitelistAndOrderSizeAreAppliedToTheSelectedPlan() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        OrderMarketQuote diamond = new OrderMarketQuote(
                "minecraft:diamond", "Diamond", new BigDecimal("10"), new BigDecimal("1000"), 64,
                new BigDecimal("1024"), new BigDecimal("1024"), 4, 10, 3, now
        );
        OrderMarketQuote emerald = new OrderMarketQuote(
                "minecraft:emerald", "Emerald", new BigDecimal("10"), new BigDecimal("900"), 64,
                new BigDecimal("4096"), new BigDecimal("8192"), 4, 10, 3, now
        );
        OrderModuleSettings settings = new OrderModuleSettings(Set.of("minecraft:diamond"),
                512, 10, 2, BigDecimal.ONE, false, 5_000);

        OrderTradePlan selected = new OrderOpportunitySelector(new OrderTradePlanner(), () -> settings)
                .best(List.of(diamond, emerald), now).orElseThrow();

        assertEquals("minecraft:diamond", selected.itemId());
        assertEquals(512, selected.orderQuantity());
    }
}
