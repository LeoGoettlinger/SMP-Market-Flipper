package com.scmarket.automation;

import com.scmarket.automation.model.ListingVerificationResult;
import com.scmarket.automation.service.GameplayActionGate;
import com.scmarket.configuration.AutomationMode;
import com.scmarket.market.opportunity.ItemFilterMode;
import com.scmarket.market.opportunity.ItemFilterPolicy;
import com.scmarket.order.OrderTradePlanner;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Lists one whitelisted inventory stack at $1 below a fresh comparable AH ask. */
public final class AhInventorySellerController {
    private static final long RETRY_DELAY_NANOS = 30_000_000_000L;
    private static final long ACTION_DELAY_NANOS = 1_000_000_000L;

    private final Minecraft client;
    private final BooleanSupplier authorized;
    private final Supplier<ItemFilterPolicy> itemFilters;
    private final Function<ItemStack, CompletableFuture<Optional<BigDecimal>>> cheapestComparableStackPrice;
    private final MinecraftAuctionInteractionAdapter auctionAdapter;
    private final GameplayActionGate gameplayActionGate;
    private final OrderTradePlanner pricing = new OrderTradePlanner();
    private volatile boolean running;
    private volatile boolean actionInFlight;
    private volatile long nextActionNanos;
    private volatile String status = "AH Seller is stopped.";

    public AhInventorySellerController(
            Minecraft client,
            BooleanSupplier authorized,
            Supplier<ItemFilterPolicy> itemFilters,
            Function<ItemStack, CompletableFuture<Optional<BigDecimal>>> cheapestComparableStackPrice,
            MinecraftAuctionInteractionAdapter auctionAdapter,
            GameplayActionGate gameplayActionGate
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
        this.itemFilters = Objects.requireNonNull(itemFilters, "itemFilters");
        this.cheapestComparableStackPrice = Objects.requireNonNull(
                cheapestComparableStackPrice, "cheapestComparableStackPrice");
        this.auctionAdapter = Objects.requireNonNull(auctionAdapter, "auctionAdapter");
        this.gameplayActionGate = Objects.requireNonNull(gameplayActionGate, "gameplayActionGate");
    }

    public void start(AutomationMode mode) {
        if (mode == null || !mode.isAutomatic()) {
            status = "Choose Auto or Smart before starting AH Seller.";
            return;
        }
        if (!isAuthorized()) {
            status = "AH Seller blocked: allowlist this exact server and configure its private AH profile.";
            return;
        }
        running = true;
        nextActionNanos = System.nanoTime();
        status = "AH Seller armed; only explicitly whitelisted inventory items can be listed.";
    }

    public void stop() {
        running = false;
        status = "AH Seller is stopped.";
    }

    public void tick() {
        if (!running || actionInFlight) {
            return;
        }
        if (!isAuthorized()) {
            running = false;
            status = "AH Seller stopped because its server/profile authorization changed.";
            return;
        }
        long now = System.nanoTime();
        if (now < nextActionNanos) {
            return;
        }
        if (!gameplayActionGate.availableTo(GameplayActionGate.Owner.AH_SELLER)) {
            status = "AH Seller paused while another module owns the Minecraft GUI.";
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null || client.getConnection() == null) {
            status = "AH Seller paused: not connected to a world.";
            return;
        }
        if (client.gui.screen() != null) {
            status = "AH Seller paused while a screen is open.";
            return;
        }
        Optional<ItemFilterPolicy> policy = currentWhitelist();
        if (policy.isEmpty()) {
            status = "AH Seller needs a non-empty Whitelist-only item filter; no inventory item was touched.";
            return;
        }
        ItemStack selected = findWhitelistedStack(player, policy.orElseThrow());
        if (selected.isEmpty()) {
            status = "No whitelisted inventory stack is ready to list.";
            nextActionNanos = now + ACTION_DELAY_NANOS;
            return;
        }
        Optional<GameplayActionGate.Lease> lease = gameplayActionGate
                .tryAcquire(GameplayActionGate.Owner.AH_SELLER);
        if (lease.isEmpty()) {
            status = "AH Seller paused while another module owns the Minecraft GUI.";
            return;
        }
        actionInFlight = true;
        ItemStack immutable = selected.copy();
        CompletableFuture<Optional<BigDecimal>> quote;
        try {
            quote = Objects.requireNonNull(cheapestComparableStackPrice.apply(immutable), "AH price future");
        } catch (RuntimeException failure) {
            finishQuoteFailure(lease.orElseThrow(), now,
                    "Fresh AH price lookup failed; the inventory stack was kept.");
            return;
        }
        quote.whenComplete((lowestAsk, error) -> client.execute(() -> {
            if (error != null || lowestAsk == null || lowestAsk.isEmpty()) {
                finishQuoteFailure(lease.orElseThrow(), System.nanoTime(),
                        "No fresh comparable AH ask; the inventory stack was kept for a later retry.");
                return;
            }
            Optional<BigDecimal> undercut = pricing.ahStackListingPrice(lowestAsk.orElseThrow());
            if (undercut.isEmpty()) {
                finishQuoteFailure(lease.orElseThrow(), System.nanoTime(),
                        "Cheapest comparable ask is too low to undercut safely; stack kept.");
                return;
            }
            if (!isAuthorized() || !stillWhitelisted(immutable)) {
                finishQuoteFailure(lease.orElseThrow(), System.nanoTime(),
                        "Authorization or whitelist changed before listing; stack kept.");
                return;
            }
            auctionAdapter.listInventoryStack(immutable, undercut.orElseThrow(),
                            () -> isAuthorized() && stillWhitelisted(immutable))
                    .whenComplete((result, listingError) -> client.execute(() -> {
                        lease.orElseThrow().close();
                        actionInFlight = false;
                        nextActionNanos = System.nanoTime() + (listingError == null
                                && result != null && result.verified() ? ACTION_DELAY_NANOS : RETRY_DELAY_NANOS);
                        if (listingError == null && result != null && result.verified()) {
                            status = "Whitelisted stack listed at $1 below the fresh comparable AH ask.";
                        } else {
                            running = false;
                            status = "AH listing was not verified; seller stopped to prevent a duplicate listing.";
                        }
                    }));
        }));
    }

    public boolean isRunning() {
        return running;
    }

    public String status() {
        return status;
    }

    private void finishQuoteFailure(GameplayActionGate.Lease lease, long now, String message) {
        lease.close();
        actionInFlight = false;
        nextActionNanos = now + RETRY_DELAY_NANOS;
        status = message;
    }

    private Optional<ItemFilterPolicy> currentWhitelist() {
        try {
            ItemFilterPolicy policy = Objects.requireNonNull(itemFilters.get(), "item filter policy");
            return policy.mode() == ItemFilterMode.WHITELIST_ONLY
                    && !policy.whitelistedItemIds().isEmpty() ? Optional.of(policy) : Optional.empty();
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private ItemStack findWhitelistedStack(LocalPlayer player, ItemFilterPolicy policy) {
        for (int slot = 0; slot < Math.min(36, player.getInventory().getContainerSize()); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (policy.evaluate(itemId).allowed()) {
                    return stack.copy();
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean stillWhitelisted(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Optional<ItemFilterPolicy> policy = currentWhitelist();
        return policy.isPresent() && policy.orElseThrow().evaluate(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).allowed();
    }

    private boolean isAuthorized() {
        try {
            return authorized.getAsBoolean();
        } catch (RuntimeException unavailableAuthorization) {
            return false;
        }
    }
}
