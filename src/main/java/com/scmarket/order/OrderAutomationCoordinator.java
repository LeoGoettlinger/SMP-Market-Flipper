package com.scmarket.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import com.scmarket.automation.service.GameplayActionGate;
import com.scmarket.configuration.OrderModuleSettings;

/**
 * Serial, fail-closed orchestration for Order→AH. One full order is collected and listed before
 * another order is collected; every order and every AH stack receives a fresh market read.
 */
public final class OrderAutomationCoordinator {
    private static final int MAXIMUM_REMAINDER_RELISTS = 4;
    private static final Duration DELIVERY_EXPIRY_WARNING_AGE = Duration.ofDays(4);

    private final OrderExecutionPort port;
    private final OrderOpportunitySelector selector;
    private final OrderCapacityPolicy capacityPolicy;
    private final OrderCompletionObservationRegistry observations;
    private final BooleanSupplier enabledAndAuthorized;
    private final Clock clock;
    private final GameplayActionGate gameplayActionGate;
    private final Supplier<OrderModuleSettings> moduleSettings;
    private final AtomicBoolean cycleInFlight = new AtomicBoolean();
    private final AtomicBoolean manualReviewRequired = new AtomicBoolean();

    public OrderAutomationCoordinator(
            OrderExecutionPort port,
            OrderOpportunitySelector selector,
            OrderCapacityPolicy capacityPolicy,
            OrderCompletionObservationRegistry observations,
            BooleanSupplier enabledAndAuthorized,
            Clock clock
    ) {
        this(port, selector, capacityPolicy, observations, enabledAndAuthorized, clock,
                new GameplayActionGate(), OrderModuleSettings::defaults);
    }

    public OrderAutomationCoordinator(
            OrderExecutionPort port,
            OrderOpportunitySelector selector,
            OrderCapacityPolicy capacityPolicy,
            OrderCompletionObservationRegistry observations,
            BooleanSupplier enabledAndAuthorized,
            Clock clock,
            GameplayActionGate gameplayActionGate
    ) {
        this(port, selector, capacityPolicy, observations, enabledAndAuthorized, clock,
                gameplayActionGate, OrderModuleSettings::defaults);
    }

    public OrderAutomationCoordinator(
            OrderExecutionPort port,
            OrderOpportunitySelector selector,
            OrderCapacityPolicy capacityPolicy,
            OrderCompletionObservationRegistry observations,
            BooleanSupplier enabledAndAuthorized,
            Clock clock,
            GameplayActionGate gameplayActionGate,
            Supplier<OrderModuleSettings> moduleSettings
    ) {
        this.port = Objects.requireNonNull(port, "port");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.capacityPolicy = Objects.requireNonNull(capacityPolicy, "capacityPolicy");
        this.observations = Objects.requireNonNull(observations, "observations");
        this.enabledAndAuthorized = Objects.requireNonNull(enabledAndAuthorized, "enabledAndAuthorized");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.gameplayActionGate = Objects.requireNonNull(gameplayActionGate, "gameplayActionGate");
        this.moduleSettings = Objects.requireNonNull(moduleSettings, "moduleSettings");
    }

    public CompletableFuture<OrderCycleResult> runCycle() {
        if (manualReviewRequired.get()) {
            return CompletableFuture.completedFuture(OrderCycleResult.idle(
                    "Order→AH is blocked after an uncertain GUI mutation; inspect inventory and orders manually."));
        }
        if (!stillAuthorized()) {
            return CompletableFuture.completedFuture(OrderCycleResult.idle(
                    "Order→AH is paused until the global switch, module, private-server profile, allowlist, "
                            + "and session authorization are all valid."));
        }
        if (!cycleInFlight.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(OrderCycleResult.idle("A market GUI action is already in progress."));
        }
        Optional<GameplayActionGate.Lease> lease = gameplayActionGate
                .tryAcquire(GameplayActionGate.Owner.ORDER_TO_AH);
        if (lease.isEmpty()) {
            cycleInFlight.set(false);
            return CompletableFuture.completedFuture(OrderCycleResult.idle(
                    "Another market module currently owns the Minecraft GUI."));
        }
        GameplayActionGate.Lease activeLease = lease.orElseThrow();
        CompletableFuture<OrderCycleResult> cycle = port.refreshOrders()
                .thenCompose(orders -> afterOrderRefresh(List.copyOf(orders)))
                .exceptionallyCompose(error -> port.returnToSafeScreen()
                        .handle((ignored, cleanupError) -> OrderCycleResult.failed("Order→AH cycle stopped safely: "
                                + rootMessage(error))))
                .whenComplete((result, error) -> {
                    if (error != null || (result != null && result.actionAttempted() && !result.succeeded())) {
                        manualReviewRequired.set(true);
                    }
                    activeLease.close();
                    cycleInFlight.set(false);
                });
        return cycle;
    }

    public boolean isBusy() {
        return cycleInFlight.get();
    }

    private CompletableFuture<OrderCycleResult> afterOrderRefresh(List<TrackedOrder> orders) {
        if (!stillAuthorized()) {
            return CompletableFuture.completedFuture(OrderCycleResult.idle("Authorization changed; no GUI action taken."));
        }
        Optional<TrackedOrder> expiringPartial = orders.stream()
                .filter(order -> order.state() == TrackedOrderState.PARTIALLY_FILLED)
                .filter(order -> Duration.between(order.earliestDeliveryAt(), clock.instant())
                        .compareTo(DELIVERY_EXPIRY_WARNING_AGE) >= 0)
                .min(Comparator.comparing(TrackedOrder::earliestDeliveryAt));
        if (expiringPartial.isPresent()) {
            return CompletableFuture.completedFuture(OrderCycleResult.idle(
                    "Urgent: a partial delivery is close to the server's five-day expiry. Automatic collection "
                            + "is held to the configured full-order policy; review order "
                            + expiringPartial.orElseThrow().orderId() + " now."));
        }

        Optional<TrackedOrder> completed = orders.stream()
                .filter(order -> order.state() == TrackedOrderState.FILLED_UNCLAIMED
                        && order.filledQuantity() == order.totalQuantity())
                .min(Comparator.comparing(TrackedOrder::createdAt).thenComparing(TrackedOrder::orderId));
        if (completed.isPresent()) {
            return processOneCompletedOrder(completed.orElseThrow());
        }
        return placeOneFreshOrder(orders);
    }

    private CompletableFuture<OrderCycleResult> placeOneFreshOrder(List<TrackedOrder> orders) {
        if (!stillAuthorized()) {
            return CompletableFuture.completedFuture(OrderCycleResult.idle("Authorization changed before quote refresh."));
        }
        List<OrderMarketQuote> quotes = new ArrayList<>();
        return port.refreshMarketQuotes().thenCompose(freshQuotes -> {
            OrderModuleSettings currentSettings;
            try {
                currentSettings = Objects.requireNonNull(moduleSettings.get(), "current module settings");
            } catch (RuntimeException unavailable) {
                return CompletableFuture.completedFuture(OrderCycleResult.idle(
                        "Order→AH settings are unavailable; no order was created."));
            }
            for (OrderMarketQuote quote : freshQuotes) {
                if (currentSettings.allowsItem(quote.itemId())
                        && capacityPolicy.canPlace(orders, quote.itemId(), currentSettings).allowed()) {
                    quotes.add(quote);
                }
            }
            Optional<OrderTradePlan> selected = selector.best(quotes, clock.instant(), currentSettings);
            if (selected.isEmpty()) {
                return CompletableFuture.completedFuture(OrderCycleResult.idle(
                        "No fresh item currently passes the profit, evidence, fill-rate and AH sales-speed gates."));
            }
            OrderTradePlan plan = selected.orElseThrow();
            OrderModuleSettings currentSettings;
            try {
                currentSettings = Objects.requireNonNull(moduleSettings.get(), "current module settings");
            } catch (RuntimeException unavailable) {
                return CompletableFuture.completedFuture(OrderCycleResult.idle(
                        "Order→AH settings are unavailable; no order was created."));
            }
            if (!stillAuthorized()) {
                return CompletableFuture.completedFuture(OrderCycleResult.idle(
                        "Authorization changed before order confirmation; no order was created."));
            }
            if (plan.orderQuantity() != currentSettings.orderQuantity()
                    || plan.expectedProfitPerItem().compareTo(currentSettings.minimumProfitPerItem()) < 0
                    || !currentSettings.allowsItem(plan.itemId())
                    || !capacityPolicy.canPlace(orders, plan.itemId(), currentSettings).allowed()) {
                return CompletableFuture.completedFuture(OrderCycleResult.idle(
                        "Order settings changed while prices were refreshed; re-evaluate on the next cycle."));
            }
            return port.placeOrder(plan).thenApply(created -> {
                if (!created.itemId().equals(plan.itemId())
                        || created.totalQuantity() != plan.orderQuantity()
                        || created.pricePerItem().compareTo(plan.orderPricePerItem()) != 0
                        || created.state() == TrackedOrderState.CANCELLED
                        || created.state() == TrackedOrderState.COLLECTED
                        || created.state() == TrackedOrderState.SOLD) {
                    return OrderCycleResult.failed("Server order differs from the fresh approved plan; automation stopped.");
                }
                return new OrderCycleResult(true, true,
                        "Placed one " + plan.orderQuantity() + "-item order for " + plan.itemName()
                                + "; all prices will be fetched again before the next order.");
            });
        });
    }

    private CompletableFuture<OrderCycleResult> processOneCompletedOrder(TrackedOrder order) {
        if (!stillAuthorized()) {
            return CompletableFuture.completedFuture(OrderCycleResult.idle("Authorization changed before collection."));
        }
        OrderCompletionObservation observation = new OrderCompletionObservation(
                order.orderId(), order.itemId(), order.totalQuantity(),
                order.pricePerItem(), order.pricePerItem()
                        .multiply(BigDecimal.valueOf(order.totalQuantity())), clock.instant());
        return port.recordOrderCompletion(observation).thenCompose(ignored -> {
            observations.markRecorded(order.orderId());
            if (!stillAuthorized()) {
                return CompletableFuture.completedFuture(OrderCycleResult.idle(
                        "Authorization changed before collection; completed order remains unclaimed."));
            }
            return port.collectOrder(order).thenCompose(collected -> {
                if (collected.isEmpty()) {
                    return CompletableFuture.completedFuture(OrderCycleResult.failed(
                            "Completed order was not collected and verified; no other order will be touched."));
                }
                CollectedOrderItems items = collected.orElseThrow();
                if (!items.orderId().equals(order.orderId()) || !items.itemId().equals(order.itemId())
                        || items.expectedQuantity() != order.totalQuantity()) {
                    return CompletableFuture.completedFuture(OrderCycleResult.failed(
                            "Collected inventory does not match the selected order; sale stopped."));
                }
                return listStacksSequentially(items, 0).thenApply(success -> success
                        ? new OrderCycleResult(true, true,
                        "One complete order was collected and its inventory was listed stackwise in the AH.")
                        : OrderCycleResult.failed("AH stack listing could not be fully verified; no next order was collected."));
            });
        });
    }

    private CompletableFuture<Boolean> listStacksSequentially(CollectedOrderItems items, int index) {
        if (index >= items.stacks().size()) {
            return CompletableFuture.completedFuture(true);
        }
        OrderSaleStack stack = items.stacks().get(index);
        return listStackWithFreshPrice(stack, 0).thenCompose(listed -> {
            if (!listed) {
                return CompletableFuture.completedFuture(false);
            }
            return listStacksSequentially(items, index + 1);
        });
    }

    private CompletableFuture<Boolean> listStackWithFreshPrice(OrderSaleStack stack, int retry) {
        if (!stillAuthorized() || retry > MAXIMUM_REMAINDER_RELISTS) {
            return CompletableFuture.completedFuture(false);
        }
        return port.refreshLowestAhStackPrice(stack).thenCompose(lowest -> {
            if (lowest.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            Optional<BigDecimal> undercut = new OrderTradePlanner().ahStackListingPrice(lowest.orElseThrow());
            if (undercut.isEmpty() || !stillAuthorized()) {
                return CompletableFuture.completedFuture(false);
            }
            return port.listStack(stack, undercut.orElseThrow()).thenCompose(receipt -> {
                if (!receipt.verified() || receipt.listedCount() < 1
                        || receipt.listedCount() + receipt.remainingCount() != stack.count()) {
                    return CompletableFuture.completedFuture(false);
                }
                if (receipt.remainingCount() == 0) {
                    return CompletableFuture.completedFuture(true);
                }
                // A residue is re-read and relisted at a newly undercut price; never reuse the previous quote.
                OrderSaleStack remainder = new OrderSaleStack(
                        stack.orderId(), stack.itemId(), stack.fingerprint(), receipt.remainingCount());
                return listStackWithFreshPrice(remainder, retry + 1);
            });
        });
    }

    private boolean stillAuthorized() {
        try {
            return enabledAndAuthorized.getAsBoolean();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return Objects.requireNonNullElse(current.getMessage(), current.getClass().getSimpleName());
    }
}
