package com.scmarket.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAutomationCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void collectsOnlyOneCompletedOrderAndRefreshesPriceForEveryStack() {
        FakePort port = new FakePort();
        port.orders.add(order("order-a", TrackedOrderState.FILLED_UNCLAIMED));
        port.orders.add(order("order-b", TrackedOrderState.FILLED_UNCLAIMED));
        OrderAutomationCoordinator coordinator = new OrderAutomationCoordinator(
                port, new OrderOpportunitySelector(), new OrderCapacityPolicy(),
                new OrderCompletionObservationRegistry(), () -> true,
                Clock.fixed(NOW, ZoneOffset.UTC));

        OrderCycleResult first = coordinator.runCycle().join();

        assertTrue(first.succeeded());
        assertEquals(1, port.collectedOrderIds.size());
        assertEquals("order-a", port.collectedOrderIds.getFirst());
        assertEquals(16, port.priceRefreshes);
        assertEquals(16, port.listedPrices.size());
        assertEquals(16, port.recordedOrderIds.size());
        assertEquals(new BigDecimal("99"), port.listedPrices.getFirst());
        assertEquals(new BigDecimal("84"), port.listedPrices.getLast());

        OrderCycleResult second = coordinator.runCycle().join();
        assertTrue(second.succeeded());
        assertEquals(List.of("order-a", "order-b"), port.collectedOrderIds);
        assertEquals(32, port.priceRefreshes);
    }

    private static TrackedOrder order(String id, TrackedOrderState state) {
        return new TrackedOrder(id, "minecraft:diamond", "Diamond", 1_024, 1_024,
                new BigDecimal("10"), state, NOW.minusSeconds(10), NOW.minusSeconds(5));
    }

    private static final class FakePort implements OrderExecutionPort {
        private final List<TrackedOrder> orders = new ArrayList<>();
        private final List<String> collectedOrderIds = new ArrayList<>();
        private final List<String> recordedOrderIds = new ArrayList<>();
        private final List<BigDecimal> listedPrices = new ArrayList<>();
        private int priceRefreshes;

        @Override
        public CompletableFuture<List<TrackedOrder>> refreshOrders() {
            return CompletableFuture.completedFuture(List.copyOf(orders));
        }

        @Override
        public CompletableFuture<List<OrderMarketQuote>> refreshMarketQuotes() {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<TrackedOrder> placeOrder(OrderTradePlan plan) {
            throw new AssertionError("No order should be created while completed orders are pending");
        }

        @Override
        public CompletableFuture<Optional<CollectedOrderItems>> collectOrder(TrackedOrder order) {
            collectedOrderIds.add(order.orderId());
            orders.replaceAll(value -> value.orderId().equals(order.orderId())
                    ? new TrackedOrder(value.orderId(), value.itemId(), value.itemName(), value.totalQuantity(),
                    value.filledQuantity(), value.pricePerItem(), TrackedOrderState.COLLECTED,
                    value.createdAt(), value.earliestDeliveryAt()) : value);
            List<OrderSaleStack> stacks = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                stacks.add(new OrderSaleStack(order.orderId(), order.itemId(),
                        "fingerprint-" + index, 64));
            }
            return CompletableFuture.completedFuture(Optional.of(
                    new CollectedOrderItems(order.orderId(), order.itemId(), stacks)));
        }

        @Override
        public CompletableFuture<Optional<BigDecimal>> refreshLowestAhStackPrice(OrderSaleStack stack) {
            priceRefreshes++;
            return CompletableFuture.completedFuture(Optional.of(BigDecimal.valueOf(100 - priceRefreshes + 1L)));
        }

        @Override
        public CompletableFuture<OrderSaleReceipt> listStack(OrderSaleStack stack, BigDecimal exactStackPrice) {
            listedPrices.add(exactStackPrice);
            return CompletableFuture.completedFuture(new OrderSaleReceipt(true, stack.count(), 0,
                    "listed exact test stack"));
        }

        @Override
        public CompletableFuture<Void> recordOrderCompletion(OrderCompletionObservation observation) {
            recordedOrderIds.add(observation.orderId());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> returnToSafeScreen() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
