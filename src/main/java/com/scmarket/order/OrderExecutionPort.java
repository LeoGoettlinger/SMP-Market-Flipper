package com.scmarket.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Minecraft-facing boundary; implementations must revalidate the private-server profile per action. */
public interface OrderExecutionPort {
    CompletableFuture<List<TrackedOrder>> refreshOrders();

    CompletableFuture<List<OrderMarketQuote>> refreshMarketQuotes();

    /** Must verify all confirmation-GUI amounts and fee before clicking the final confirm control. */
    CompletableFuture<TrackedOrder> placeOrder(OrderTradePlan plan);

    /** Collects only the specified fully-complete order and proves an exact order-sized inventory delta. */
    CompletableFuture<Optional<CollectedOrderItems>> collectOrder(TrackedOrder order);

    /** Reads the current cheapest comparable AH stack for this exact item/stack size. */
    CompletableFuture<Optional<BigDecimal>> refreshLowestAhStackPrice(OrderSaleStack stack);

    /** Lists only this exact stack and verifies the resulting GUI/inventory delta. */
    CompletableFuture<OrderSaleReceipt> listStack(OrderSaleStack stack, BigDecimal exactStackPrice);

    /** Persists a distinct order-completion fact; it must not be inserted into AH sale history. */
    CompletableFuture<Void> recordOrderCompletion(OrderCompletionObservation observation);

    CompletableFuture<Void> returnToSafeScreen();
}
