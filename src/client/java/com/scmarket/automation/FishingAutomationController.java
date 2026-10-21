package com.scmarket.automation;

import com.scmarket.automation.model.AuctionInteractionProfile;
import com.scmarket.automation.service.GameplayActionGate;
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
    private String status = "Fishing is idle.";

    public FishingAutomationController(
            Minecraft client,
            BooleanSupplier authorized,
            Supplier<Optional<AuctionInteractionProfile>> profileSupplier,
            Function<ItemStack, CompletableFuture<Optional<BigDecimal>>> marketPrice,
            MinecraftAuctionInteractionAdapter auctionAdapter,
            LongSupplier actionDelayMillis,
            GameplayActionGate gameplayActionGate
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
        this.profileSupplier = Objects.requireNonNull(profileSupplier, "profileSupplier");
        this.marketPrice = Objects.requireNonNull(marketPrice, "marketPrice");
        this.auctionAdapter = Objects.requireNonNull(auctionAdapter, "auctionAdapter");
        this.actionDelayMillis = Objects.requireNonNull(actionDelayMillis, "actionDelayMillis");
        this.gameplayActionGate = Objects.requireNonNull(gameplayActionGate, "gameplayActionGate");
    }

    public void tick() {
        long now = System.nanoTime();
        if (!gameplayActionGate.availableTo(GameplayActionGate.Owner.FISHING)) {
            status = "Fishing paused while another market module owns the shared GUI action lease.";
            return;
        }
        Optional<AuctionInteractionProfile> profile = profileSupplier.get();
        if (!authorized.getAsBoolean()) {
            status = "Fishing paused: enable Fishing, allowlist this exact server, and arm the authorized session.";
            return;
        }
        if (profile.isEmpty()) {
            status = "Fishing paused: configure a private-server interaction profile.";
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null
                || client.getConnection() == null) {
            phase = Phase.IDLE;
            status = "Fishing paused: not connected to a world.";
            return;
        }
        if (client.screen != null) {
            status = "Fishing paused while a screen is open.";
            return;
        }

        processSaleQueue(profile.orElseThrow(), now);
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
                    cast(player, profile.orElseThrow(), now);
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
                    beforeReel.putAll(inventoryCounts(profile.orElseThrow()));
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
                    recordCatch(profile.orElseThrow(), now);
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
        saleQueue.clear();
        ownedLootCounts.clear();
        status = "Fishing stopped; no pending items will be listed automatically.";
    }

    private void cast(LocalPlayer player, AuctionInteractionProfile profile, long now) {
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

    private void recordCatch(AuctionInteractionProfile profile, long now) {
        Map<String, Integer> after = inventoryCounts(profile);
        boolean found = false;
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
                if (!saleQueue.contains(entry.getKey())) {
                    saleQueue.addLast(entry.getKey());
                }
            } else {
                status = "Catch retained: an identical item was already in inventory, so it will not be sold automatically.";
            }
        }
        phase = Phase.IDLE;
        nextActionNanos = now + actionDelayNanos();
        if (found && !saleQueue.isEmpty()) {
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
        int inventoryCount = inventoryCounts(profile).getOrDefault(fingerprint, 0);
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
            if (!authorized.getAsBoolean()) {
                saleInFlight = false;
                status = "Sale cancelled before interaction because the authorized session was disarmed.";
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
                                    int remaining = inventoryCounts(profile).getOrDefault(fingerprint, 0);
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

    private Map<String, Integer> inventoryCounts(AuctionInteractionProfile profile) {
        Map<String, Integer> counts = new HashMap<>();
        if (client.player == null) {
            return counts;
        }
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                try {
                    counts.merge(fingerprintFactory.fingerprint(stack, profile), stack.getCount(), Integer::sum);
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
