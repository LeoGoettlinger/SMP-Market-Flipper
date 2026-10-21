package com.scmarket.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies the confirmed Order/AH pricing rules. A quote is never reused beyond its freshness window;
 * callers must obtain a new quote before every order and every AH stack listing.
 */
public final class OrderTradePlanner {
    public static final int ORDER_QUANTITY = 1_024;
    public static final BigDecimal ORDER_CREATION_FEE_RATE = new BigDecimal("0.01");
    public static final BigDecimal AH_TAX_RATE = BigDecimal.ZERO;
    public static final BigDecimal MINIMUM_NET_PROFIT_PER_ITEM = BigDecimal.ONE;
    public static final BigDecimal ONE_CURRENCY_UNIT = BigDecimal.ONE;
    public static final int MINIMUM_ORDER_EVIDENCE_COUNT = 3;
    public static final int MINIMUM_AH_SALE_EVIDENCE_COUNT = 5;
    public static final int MINIMUM_DISTINCT_AH_SELLERS = 2;
    public static final Duration MAXIMUM_QUOTE_AGE = Duration.ofMinutes(2);
    /** Leaves at least a day of margin against the server's five-day delivery expiry. */
    public static final Duration MAXIMUM_PREDICTED_FILL_TIME = Duration.ofDays(4);

    public Optional<OrderTradePlan> plan(OrderMarketQuote quote, Instant now) {
        return plan(quote, now, ORDER_QUANTITY, MINIMUM_NET_PROFIT_PER_ITEM);
    }

    public Optional<OrderTradePlan> plan(
            OrderMarketQuote quote, Instant now, int orderQuantity, BigDecimal minimumProfitPerItem
    ) {
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(minimumProfitPerItem, "minimumProfitPerItem");
        if (orderQuantity < 1 || orderQuantity > 2_048
                || minimumProfitPerItem.compareTo(MINIMUM_NET_PROFIT_PER_ITEM) < 0) {
            throw new IllegalArgumentException("Order quantity or minimum profit is outside supported bounds");
        }
        Duration age = Duration.between(quote.observedAt(), now);
        if (age.isNegative() || age.compareTo(MAXIMUM_QUOTE_AGE) > 0
                || quote.orderEvidenceCount() < MINIMUM_ORDER_EVIDENCE_COUNT
                || quote.ahSaleEvidenceCount() < MINIMUM_AH_SALE_EVIDENCE_COUNT
                || quote.distinctAhSellers() < MINIMUM_DISTINCT_AH_SELLERS) {
            return Optional.empty();
        }

        BigDecimal orderPrice = quote.competingTopBidPerItem().add(ONE_CURRENCY_UNIT);
        BigDecimal deposit = orderPrice.multiply(BigDecimal.valueOf(orderQuantity));
        // The screenshot confirms a 1% fee and shows $1 on a $9 deposit. The GUI confirmation
        // remains authoritative; round the estimate up to the next whole currency unit.
        BigDecimal fee = estimateCreationFee(deposit);
        BigDecimal totalPayment = deposit.add(fee);
        BigDecimal ahStackPrice = quote.lowestComparableStackPrice().subtract(ONE_CURRENCY_UNIT);
        if (ahStackPrice.signum() <= 0) {
            return Optional.empty();
        }

        int fullStacks = orderQuantity / quote.comparableStackSize();
        int remainder = orderQuantity % quote.comparableStackSize();
        BigDecimal revenue = ahStackPrice.multiply(BigDecimal.valueOf(fullStacks));
        if (remainder > 0) {
            BigDecimal partialStackRevenue = ahStackPrice
                    .multiply(BigDecimal.valueOf(remainder))
                    .divide(BigDecimal.valueOf(quote.comparableStackSize()), 0, RoundingMode.DOWN);
            revenue = revenue.add(partialStackRevenue);
        }
        BigDecimal netProfit = revenue.subtract(totalPayment)
                .subtract(revenue.multiply(AH_TAX_RATE));
        BigDecimal profitPerItem = netProfit.divide(BigDecimal.valueOf(orderQuantity), 8, RoundingMode.DOWN);
        if (profitPerItem.compareTo(minimumProfitPerItem) < 0) {
            return Optional.empty();
        }

        BigDecimal fillHours = BigDecimal.valueOf(orderQuantity)
                .divide(quote.observedOrderFillItemsPerHour(), 8, RoundingMode.CEILING);
        if (fillHours.compareTo(BigDecimal.valueOf(MAXIMUM_PREDICTED_FILL_TIME.toHours())) > 0) {
            return Optional.empty();
        }
        BigDecimal sellHours = BigDecimal.valueOf(orderQuantity)
                .divide(quote.observedAhSaleItemsPerHour(), 8, RoundingMode.CEILING);
        Duration fillTime = durationHours(fillHours);
        Duration sellTime = durationHours(sellHours);
        BigDecimal expectedTotalHours = fillHours.add(sellHours);
        BigDecimal profitPerHour = netProfit.divide(expectedTotalHours, 8, RoundingMode.DOWN);

        return Optional.of(new OrderTradePlan(
                quote.itemId(), quote.itemName(), orderQuantity, orderPrice, deposit,
                fee, totalPayment, ahStackPrice, quote.comparableStackSize(), revenue,
                netProfit, profitPerItem, fillTime, sellTime, profitPerHour, quote
        ));
    }

    /** Uses the GUI-observed fee rule: 1%, rounded up to the next whole currency unit. */
    public BigDecimal estimateCreationFee(BigDecimal deposit) {
        Objects.requireNonNull(deposit, "deposit");
        if (deposit.signum() < 0) {
            throw new IllegalArgumentException("deposit must not be negative");
        }
        return deposit.multiply(ORDER_CREATION_FEE_RATE).setScale(0, RoundingMode.CEILING);
    }

    /** Recomputes the exact $1 undercut for a newly observed stack immediately before listing. */
    public Optional<BigDecimal> ahStackListingPrice(BigDecimal freshLowestComparableStackPrice) {
        Objects.requireNonNull(freshLowestComparableStackPrice, "freshLowestComparableStackPrice");
        if (freshLowestComparableStackPrice.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal price = freshLowestComparableStackPrice.subtract(ONE_CURRENCY_UNIT);
        return price.signum() > 0 ? Optional.of(price) : Optional.empty();
    }

    private static Duration durationHours(BigDecimal hours) {
        long seconds = hours.multiply(BigDecimal.valueOf(3_600L))
                .setScale(0, RoundingMode.CEILING).longValueExact();
        return Duration.ofSeconds(seconds);
    }
}
