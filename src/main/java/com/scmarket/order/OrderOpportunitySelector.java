package com.scmarket.order;

import com.scmarket.configuration.OrderModuleSettings;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Ranks fresh profitable opportunities and applies the current Order→AH item whitelist. */
public final class OrderOpportunitySelector {
    private final OrderTradePlanner planner;
    private final Supplier<OrderModuleSettings> settings;

    public OrderOpportunitySelector() {
        this(new OrderTradePlanner(), OrderModuleSettings::defaults);
    }

    public OrderOpportunitySelector(OrderTradePlanner planner) {
        this(planner, OrderModuleSettings::defaults);
    }

    public OrderOpportunitySelector(OrderTradePlanner planner, Supplier<OrderModuleSettings> settings) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public Optional<OrderTradePlan> best(Collection<OrderMarketQuote> quotes, Instant now) {
        return best(quotes, now, Objects.requireNonNull(settings.get(), "current settings"));
    }

    public Optional<OrderTradePlan> best(
            Collection<OrderMarketQuote> quotes, Instant now, OrderModuleSettings current
    ) {
        Objects.requireNonNull(quotes, "quotes");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(current, "current settings");
        List<OrderTradePlan> plans = quotes.stream()
                .filter(quote -> current.allowsItem(quote.itemId()))
                .map(quote -> planner.plan(quote, now, current.orderQuantity(), current.minimumProfitPerItem()))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(OrderTradePlan::expectedProfitPerHour).reversed()
                        .thenComparing(OrderTradePlan::expectedNetProfit, Comparator.reverseOrder())
                        .thenComparing(OrderTradePlan::expectedFillTime)
                        .thenComparing(OrderTradePlan::expectedAhSellTime)
                        .thenComparing(OrderTradePlan::itemId))
                .toList();
        return plans.stream().findFirst();
    }
}
