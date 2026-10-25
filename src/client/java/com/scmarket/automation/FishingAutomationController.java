package com.scmarket.automation;

import com.scmarket.automation.model.AuctionInteractionProfile;
import com.scmarket.automation.model.AutomationMode;
import com.scmarket.automation.service.GameplayActionGate;
import com.scmarket.market.opportunity.ItemFilterPolicy;
import com.scmarket.mixin.FishingBobberAccessor;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;

/** Client-tick fishing loop. It uses the server-synchronised bite flag and fails closed on uncertain loot. */
public final class FishingAutomationController {
    private static final long CAST_TIMEOUT_NANOS = 3_000_000_000L;
    private static final long REEL_TIMEOUT_NANOS = 5_000_000_000L;
    private static final long CATCH_SETTLE_NANOS = 750_000_000L;
    private static final long SALE_RETRY_NANOS = 30_000_000_000L;

    private final Minecraft client;
    private final BooleanSupplier authorized;
    private final Supplier<Optional<AuctionInteractionProfile>> profileSupplier;
    private final Function<ItemStack, CompletableFuture<Optional<BigDecimal>>> marketPrice;
    private final MinecraftAuctionInteractionAdapter auctionAdapter;
    private final LongSupplier actionDelayMillis;
    private final Supplier<AutomationMode> automationMode;
    private final Supplier<ItemFilterPolicy> itemFilters;
    private final GameplayActionGate gameplayActionGate;
    private final MinecraftStackFingerprintFactory fingerprintFactory = new MinecraftStackFingerprintFactory();
    private final Map<String, Integer> beforeReel = new HashMap<>();
    private final Map<String, Integer> ownedLootCounts = new HashMap<>();
    private final ArrayDeque<String> saleQueue = new ArrayDeque<>();
    private Phase phase = Phase.IDLE;
    private long deadlineNanos;
    private long nextActionNanos;
    private long nextSaleRetryNanos;
    private boolean saleInFlight;
    private boolean beforeReelProfiled;
    private String status = "Fishing is idle.";

    public FishingAutomationController(
            Minecraft client,
            BooleanSupplier authorized,
            Supplier<Optional<AuctionInteractionProfile>> profileSupplier,
            Function<ItemStack, CompletableFuture<Optional<BigDecimal>>> marketPrice,
            MinecraftAuctionInteractionAdapter auctionAdapter,
            LongSupplier actionDelayMillis,
            Supplier<AutomationMode> automationMode,
            Supplier<ItemFilterPolicy> itemFilters,
            GameplayActionGate gameplayActionGate
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
        this.profileSupplier = Objects.requireNonNull(profileSupplier, "profileSupplier");
        this.marketPrice = Objects.requireNonNull(marketPrice, "marketPrice");
        this.auctionAdapter = Objects.requireNonNull(auctionAdapter, "auctionAdapter");
        this.actionDelayMillis = Objects.requireNonNull(actionDelayMillis, "actionDelayMillis");
        this.automationMode = Objects.requireNonNull(automationMode, "automationMode");
        this.itemFilters = Objects.requireNonNull(itemFilters, "itemFilters");
        this.gameplayActionGate = Objects.requireNonNull(gameplayActionGate, "gameplayActionGate");
    }

    public void tick() {
        long now = System.nanoTime();
        if (!gameplayActionGate.availableTo(GameplayActionGate.Owner.FISHING)) {
            status = "Fishing paused while another market module owns the shared GUI action lease.";
            return;
        }
        Optional<AuctionInteractionProfile> profile = currentProfile();
        if (!isAuthorized()) {
            phase = Phase.IDLE;
            beforeReel.clear();
            beforeReelProfiled = false;
            status = "Fishing paused: enable Fishing and allowlist this exact server.";
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null
                || client.getConnection() == null) {
            phase = Phase.IDLE;
            status = "Fishing paused: not connected to a world.";
            return;
        }
        if (client.gui.screen() != null) {
            status = "Fishing paused while a screen is open.";
            return;
        }

        if (profile.isPresent()) {
            processSaleQueue(profile.orElseThrow(), now);
        } else if (!saleQueue.isEmpty()) {
            status = "Fishing continues; automatic listing is paused until a private auction profile is configured.";
        }
        if (saleInFlight) {
            return;
        }

        FishingHook bobber = player.fishing;
        switch (phase) {
            case IDLE -> {
                if (bobber != null) {
                    phase = Phase.WAITING_FOR_BITE;
                    status = "Fishing: bobber is in the water; waiting for a bite.";
                } else if (now >= nextActionNanos && saleQueue.isEmpty()) {
                    cast(player, profile, now);
                }
            }
            case CASTING -> {
                if (bobber != null) {
                    phase = Phase.WAITING_FOR_BITE;
                    status = "Fishing: bobber is in the water; waiting for a bite.";
                } else if (now >= deadlineNanos) {
                    phase = Phase.IDLE;
                    nextActionNanos = now + actionDelayNanos();
                    status = "Fishing rod did not cast; retrying after the configured delay.";
                }
            }
            case WAITING_FOR_BITE -> {
                if (bobber == null) {
                    phase = Phase.IDLE;
                    nextActionNanos = now + actionDelayNanos();
                    status = "Fishing bobber disappeared; waiting before recasting.";
                } else if (((FishingBobberAccessor) bobber).scmarket$isCaughtFish()) {
                    int rodSlot = findFishingRod(player);
                    if (rodSlot < 0) {
                        status = "Bite detected, but the fishing rod is no longer in the hotbar; reeling paused.";
                        return;
                    }
                    player.getInventory().setSelectedSlot(rodSlot);
                    beforeReel.clear();
                    beforeReel.putAll(inventoryCounts(profile));
                    beforeReelProfiled = profile.isPresent();
                    phase = Phase.REEL_PENDING;
                    deadlineNanos = now + actionDelayNanos();
                    status = "Bite detected; reeling after the configured action delay.";
                }
            }
            case REEL_PENDING -> {
                if (bobber == null) {
                    beginCatchSettlement(now);
                } else if (now >= deadlineNanos) {
                    int rodSlot = findFishingRod(player);
                    if (rodSlot < 0) {
                        status = "Bite detected, but the fishing rod is no longer in the hotbar; reeling paused.";
                        return;
                    }
                    Optional<GameplayActionGate.Lease> lease = gameplayActionGate
                            .tryAcquire(GameplayActionGate.Owner.FISHING);
                    if (lease.isEmpty()) {
                        status = "Fishing paused while another market module owns the shared GUI action lease.";
                        return;
                    }
                    try (GameplayActionGate.Lease ignored = lease.orElseThrow()) {
                        player.getInventory().setSelectedSlot(rodSlot);
                        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                        phase = Phase.REELING;
                        deadlineNanos = now + REEL_TIMEOUT_NANOS;
                        status = "Reeled in; checking the inventory for the exact catch.";
                    }
                }
            }
            case REELING -> {
                if (bobber == null) {
                    beginCatchSettlement(now);
                } else if (now >= deadlineNanos) {
                    phase = Phase.IDLE;
                    nextActionNanos = now + actionDelayNanos();
                    status = "Reel was not confirmed by the server; no sale was attempted.";
                }
            }
            case SETTLING_CATCH -> {
                if (now >= deadlineNanos) {
                    recordCatch(profile, now);
                }
            }
        }
    }

    public String status() {
        return status;
    }

    public void stop() {
        phase = Phase.IDLE;
        saleInFlight = false;
        beforeReel.clear();
        beforeReelProfiled = false;
        saleQueue.clear();
        ownedLootCounts.clear();
        status = "Fishing stopped; no pending items will be listed automatically.";
    }

    private void cast(
            LocalPlayer player, Optional<AuctionInteractionProfile> profile, long now
    ) {
        int rodSlot = findFishingRod(player);
        if (rodSlot < 0) {
            status = "Fishing paused: place a fishing rod in the hotbar.";
            return;
        }
        boolean hasSpace = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                hasSpace = true;
                break;
            }
        }
        if (!hasSpace) {
            status = "Fishing paused: make at least one inventory slot available to prevent dropped catches.";
            return;
        }
        Optional<GameplayActionGate.Lease> lease = gameplayActionGate.tryAcquire(GameplayActionGate.Owner.FISHING);
        if (lease.isEmpty()) {
            status = "Fishing paused while another market module owns the shared GUI action lease.";
            return;
        }
        try (GameplayActionGate.Lease ignored = lease.orElseThrow()) {
            player.getInventory().setSelectedSlot(rodSlot);
            beforeReel.clear();
            beforeReel.putAll(inventoryCounts(profile));
            beforeReelProfiled = profile.isPresent();
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            phase = Phase.CASTING;
            deadlineNanos = now + CAST_TIMEOUT_NANOS;
            status = "Fishing rod cast; waiting for the server bobber.";
        }
    }

    private void beginCatchSettlement(long now) {
        phase = Phase.SETTLING_CATCH;
        deadlineNanos = now + CATCH_SETTLE_NANOS;
    }

    private void recordCatch(Optional<AuctionInteractionProfile> profile, long now) {
        Map<String, Integer> after = inventoryCounts(profile);
        if (beforeReelProfiled != profile.isPresent()) {
            phase = Phase.IDLE;
            nextActionNanos = now + actionDelayNanos();
            status = "Fishing profile changed during a cast; keeping inventory safe and skipping automatic sale.";
            beforeReel.clear();
            beforeReelProfiled = false;
            return;
        }
        if (profile.isEmpty()) {
            boolean found = after.entrySet().stream()
                    .anyMatch(entry -> entry.getValue() > beforeReel.getOrDefault(entry.getKey(), 0));
            phase = Phase.IDLE;
            nextActionNanos = now + actionDelayNanos();
            status = found
                    ? "Catch detected and kept in inventory; automatic sale needs a verified private auction profile."
                    : "No inventory catch was confirmed; no sale was attempted.";
            beforeReel.clear();
            beforeReelProfiled = false;
            return;
        }
        boolean found = false;
        boolean filterExcluded = false;
        for (Map.Entry<String, Integer> entry : after.entrySet()) {
            int previous = beforeReel.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() <= previous) {
                continue;
            }
            found = true;
            int caughtCount = entry.getValue() - previous;
            if (previous == 0) {
                ownedLootCounts.put(entry.getKey(), caughtCount);
            } else if (ownedLootCounts.containsKey(entry.getKey())) {
                ownedLootCounts.merge(entry.getKey(), caughtCount, Integer::sum);
            }
            if (ownedLootCounts.containsKey(entry.getKey())) {
                if (requiresSmartFilter()) {
                    ItemStack catchStack = findStack(entry.getKey(), profile.orElseThrow());
                    if (catchStack.isEmpty() || !smartFilterAllows(catchStack)) {
                        ownedLootCounts.remove(entry.getKey());
                        filterExcluded = true;
                        continue;
                    }
                }
                if (!saleQueue.contains(entry.getKey())) {
                    saleQueue.addLast(entry.getKey());
                }
            } else {
                status = "Catch retained: an identical item was already in inventory, so it will not be sold automatically.";
            }
        }
        phase = Phase.IDLE;
        nextActionNanos = now + actionDelayNanos();
        if (filterExcluded) {
            status = saleQueue.isEmpty()
                    ? "Smart mode kept the filter-excluded catch in inventory."
                    : "Smart mode kept the filter-excluded catch; checking other eligible catches.";
        } else if (found && !saleQueue.isEmpty()) {
            status = "Catch detected; checking recent market evidence before listing.";
        } else if (!found) {
            status = "No inventory catch was confirmed; no sale was attempted.";
        }
    }

    private void processSaleQueue(AuctionInteractionProfile profile, long now) {
        if (saleQueue.isEmpty() || saleInFlight || now < nextSaleRetryNanos) {
            return;
        }
        String fingerprint = saleQueue.peekFirst();
        int inventoryCount = inventoryCounts(Optional.of(profile)).getOrDefault(fingerprint, 0);
        Integer ownedCount = ownedLootCounts.get(fingerprint);
        if (inventoryCount <= 0 || ownedCount == null) {
            saleQueue.removeFirst();
            ownedLootCounts.remove(fingerprint);
            return;
        }
        if (inventoryCount > ownedCount) {
            saleQueue.removeFirst();
            ownedLootCounts.remove(fingerprint);
            status = "Catch retained: matching inventory changed outside Fishing; automatic sale was disabled for this item.";
            return;
        }
        if (inventoryCount < ownedCount) {
            ownedLootCounts.put(fingerprint, inventoryCount);
        }
        ItemStack stack = findStack(fingerprint, profile);
        if (stack.isEmpty()) {
            saleQueue.removeFirst();
            ownedLootCounts.remove(fingerprint);
            return;
        }
        if (requiresSmartFilter() && !smartFilterAllows(stack)) {
            saleQueue.removeFirst();
            ownedLootCounts.remove(fingerprint);
            status = "Smart mode retained this catch after a filter change; no listing was attempted.";
            return;
        }
        saleInFlight = true;
        ItemStack immutable = stack.copy();
        CompletableFuture<Optional<BigDecimal>> quote;
        try {
            quote = Objects.requireNonNull(marketPrice.apply(immutable), "market price future");
        } catch (RuntimeException failure) {
            saleInFlight = false;
            nextSaleRetryNanos = now + SALE_RETRY_NANOS;
            status = "Catch kept: market-price lookup failed safely.";
            return;
        }
        quote.whenComplete((price, error) -> client.execute(() -> {
            if (!isAuthorized()) {
                saleInFlight = false;
                status = "Sale cancelled because the current server or automation safety gates changed.";
                return;
            }
            if (error != null || price == null || price.isEmpty()) {
                saleInFlight = false;
                nextSaleRetryNanos = System.nanoTime() + SALE_RETRY_NANOS;
                status = "Catch kept: no sufficiently recent, comparable market price is available; will retry later.";
                return;
            }
            Optional<GameplayActionGate.Lease> lease = gameplayActionGate
                    .tryAcquire(GameplayActionGate.Owner.FISHING);
            if (lease.isEmpty()) {
                saleInFlight = false;
                nextSaleRetryNanos = System.nanoTime() + SALE_RETRY_NANOS;
                status = "Catch retained: another market module currently owns the GUI.";
                return;
            }
            try {
                auctionAdapter.listFishingCatch(immutable, price.orElseThrow(), authorized)
                        .whenComplete((result, listingError) -> client.execute(() -> {
                            try (GameplayActionGate.Lease ignored = lease.orElseThrow()) {
                                saleInFlight = false;
                                nextSaleRetryNanos = System.nanoTime() + SALE_RETRY_NANOS;
                                if (listingError == null && result != null && result.verified()) {
                                    saleQueue.remove(fingerprint);
                                    int remaining = inventoryCounts(Optional.of(profile)).getOrDefault(fingerprint, 0);
                                    int stillOwned = Math.max(0,
                                            ownedLootCounts.getOrDefault(fingerprint, 0) - immutable.getCount());
                                    if (remaining <= 0 || stillOwned <= 0) {
                                        ownedLootCounts.remove(fingerprint);
                                    } else {
                                        ownedLootCounts.put(fingerprint, Math.min(remaining, stillOwned));
                                        saleQueue.addLast(fingerprint);
                                    }
                                    status = result.message();
                                } else {
                                    status = "Catch retained: listing was not verified. "
                                            + (listingError == null && result != null
                                            ? result.message() : "The auction interaction failed safely.");
                                }
                            }
                        }));
            } catch (RuntimeException failure) {
                lease.orElseThrow().close();
                saleInFlight = false;
                nextSaleRetryNanos = System.nanoTime() + SALE_RETRY_NANOS;
                status = "Catch retained: listing could not start; no retry was sent immediately.";
            }
        }));
    }

    private Optional<AuctionInteractionProfile> currentProfile() {
        try {
            Optional<AuctionInteractionProfile> current = profileSupplier.get();
            return current == null ? Optional.empty() : current;
        } catch (RuntimeException unavailableProfile) {
            return Optional.empty();
        }
    }

    private boolean isAuthorized() {
        try {
            return authorized.getAsBoolean();
        } catch (RuntimeException unavailableAuthorization) {
            return false;
        }
    }

    private boolean requiresSmartFilter() {
        try {
            return automationMode.get().isSmart();
        } catch (RuntimeException unavailableMode) {
            return true;
        }
    }

    private boolean smartFilterAllows(ItemStack stack) {
        try {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            return itemFilters.get().evaluate(itemId).allowed();
        } catch (RuntimeException unavailableFilter) {
            return false;
        }
    }

    private Map<String, Integer> inventoryCounts(Optional<AuctionInteractionProfile> profile) {
        Map<String, Integer> counts = new HashMap<>();
        if (client.player == null) {
            return counts;
        }
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                try {
                    String fingerprint = profile.isPresent()
                            ? fingerprintFactory.fingerprint(stack, profile.orElseThrow())
                            : fingerprintFactory.observationFingerprint(stack);
                    counts.merge(fingerprint, stack.getCount(), Integer::sum);
                } catch (RuntimeException ignored) {
                    // An item that cannot be normalized is never eligible for automatic sale.
                }
            }
        }
        return counts;
    }

    private ItemStack findStack(String fingerprint, AuctionInteractionProfile profile) {
        if (client.player == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                try {
                    if (fingerprint.equals(fingerprintFactory.fingerprint(stack, profile))) {
                        return stack;
                    }
                } catch (RuntimeException ignored) {
                    // Keep scanning; unsupported items are not sold.
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static int findFishingRod(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(slot).getItem())
                    .toString().equals("minecraft:fishing_rod")) {
                return slot;
            }
        }
        return -1;
    }

    private long actionDelayNanos() {
        long configured = Math.max(0L, Math.min(10_000L, actionDelayMillis.getAsLong()));
        return Math.max(750L, configured) * 1_000_000L;
    }

    private enum Phase {
        IDLE,
        CASTING,
        WAITING_FOR_BITE,
        REEL_PENDING,
        REELING,
        SETTLING_CATCH
    }
}
