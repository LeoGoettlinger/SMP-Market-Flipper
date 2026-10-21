package com.scmarket.packet;

import com.scmarket.api.model.ApiAuctionItem;
import com.scmarket.api.model.ApiAuctionListing;
import com.scmarket.api.model.ApiAuctionPage;
import com.scmarket.api.model.ApiArmorTrim;
import com.scmarket.api.model.ApiContainerItem;
import com.scmarket.api.model.ApiEnchantment;
import com.scmarket.api.model.ApiItemData;
import com.scmarket.api.model.ApiPaginationMetadata;
import com.scmarket.api.model.ApiSeller;
import com.scmarket.automation.model.AuctionInteractionProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/**
 * Thread-safe capture of auction house screen data intercepted from Minecraft packets.
 * The Mixin pushes data in; PacketMarketScanWork reads it out.
 */
public final class AuctionHouseDataCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("sc_market");
    private static final long PACKET_QUIET_PERIOD_NANOS = 300_000_000L;

    private int activeSyncId = -1;
    private final List<ItemStack> capturedSlots = new ArrayList<>();
    private long lastPacketAtNanos;
    private boolean hasFullSnapshot;
    private boolean capturing;

    private final Supplier<Optional<AuctionInteractionProfile>> profileSupplier;

    public AuctionHouseDataCapture(Supplier<Optional<AuctionInteractionProfile>> profileSupplier) {
        this.profileSupplier = Objects.requireNonNull(profileSupplier, "profileSupplier");
    }

    /**
     * Called by the Mixin when an OpenScreenS2CPacket arrives.
     * Returns true if this screen matches the configured AH profile.
     */
    public synchronized boolean onScreenOpened(int syncId, String screenTitle) {
        resetCapture();
        Optional<AuctionInteractionProfile> optProfile = profileSupplier.get();
        if (optProfile.isEmpty() || !optProfile.orElseThrow().resultsScreenTitle().equals(screenTitle)) {
            return false;
        }
        activeSyncId = syncId;
        capturing = true;
        lastPacketAtNanos = System.nanoTime();
        LOGGER.info("Auction house screen detected (syncId={}, title={})", syncId, screenTitle);
        return true;
    }

    /** Replaces the incomplete slot buffer only after a complete inventory packet arrives. */
    public synchronized void onInventorySynced(int syncId, List<ItemStack> slots) {
        if (!capturing || activeSyncId != syncId) {
            return;
        }
        hasFullSnapshot = false;
        capturedSlots.clear();
        slots.stream().map(ItemStack::copy).forEach(capturedSlots::add);
        lastPacketAtNanos = System.nanoTime();
        hasFullSnapshot = true;
    }

    /** Applies incremental server updates; cursor-slot updates are intentionally ignored. */
    public synchronized void onSlotUpdated(int syncId, int slot, ItemStack stack) {
        if (!capturing || activeSyncId != syncId || !hasFullSnapshot || slot < 0 || slot > 255) {
            return;
        }
        while (capturedSlots.size() <= slot) {
            capturedSlots.add(ItemStack.EMPTY);
        }
        capturedSlots.set(slot, stack.copy());
        lastPacketAtNanos = System.nanoTime();
    }

    public synchronized void onScreenClosed(int syncId) {
        if (activeSyncId == syncId) {
            resetCapture();
        }
    }

    public synchronized boolean isCapturing() {
        return capturing;
    }

    public synchronized boolean hasStableSnapshot() {
        return capturing && hasFullSnapshot
                && System.nanoTime() - lastPacketAtNanos >= PACKET_QUIET_PERIOD_NANOS;
    }

    /** Returns only a complete page that has been quiet for a short debounce period. */
    public synchronized Optional<ApiAuctionPage> captureCurrentPage() {
        if (!capturing || !hasFullSnapshot
                || System.nanoTime() - lastPacketAtNanos < PACKET_QUIET_PERIOD_NANOS) {
            return Optional.empty();
        }
        List<ApiAuctionListing> listings = extractListings(List.copyOf(capturedSlots));
        ApiPaginationMetadata pagination = new ApiPaginationMetadata(
                1, listings.size(), OptionalInt.empty(), OptionalInt.empty()
        );
        return Optional.of(new ApiAuctionPage(200, listings, pagination));
    }

    private synchronized void resetCapture() {
        activeSyncId = -1;
        capturedSlots.clear();
        hasFullSnapshot = false;
        lastPacketAtNanos = 0;
        capturing = false;
    }

    private List<ApiAuctionListing> extractListings(List<ItemStack> snapshot) {
        Optional<AuctionInteractionProfile> optProfile = profileSupplier.get();
        if (optProfile.isEmpty()) {
            return List.of();
        }
        AuctionInteractionProfile p = optProfile.orElseThrow();
        List<ApiAuctionListing> result = new ArrayList<>();
        int first = Math.max(0, p.firstResultSlot());
        int last = Math.min(snapshot.size() - 1, p.lastResultSlot());
        for (int slotId = first; slotId <= last; slotId++) {
            ItemStack stack = snapshot.get(slotId);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<ApiAuctionListing> listing = parseListing(stack, p);
            listing.ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private Optional<ApiAuctionListing> parseListing(ItemStack stack, AuctionInteractionProfile profile) {
        try {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int count = stack.getCount();
            Optional<String> displayName = Optional.of(stack.getHoverName().getString()).filter(s -> !s.isBlank());
            List<String> lore = visibleLore(stack);
            Optional<ApiItemData> itemData = parseItemData(stack);
            List<ApiContainerItem> contents = parseContents(stack);

            ApiAuctionItem item = new ApiAuctionItem(
                    Optional.of(itemId),
                    OptionalInt.of(count),
                    displayName,
                    lore,
                    itemData,
                    contents,
                    List.of()
            );

            Optional<BigDecimal> price = extractPrice(lore, profile.priceLorePrefix());
            Optional<ApiSeller> seller = extractSeller(lore, profile.sellerLorePrefix());

            return Optional.of(new ApiAuctionListing(
                    Optional.of(item),
                    price,
                    seller,
                    OptionalLong.empty()
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> extractPrice(List<String> loreLines, String prefix) {
        return loreLines.stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).strip())
                .filter(value -> !value.isEmpty())
                .findFirst()
                .map(value -> value.replace(",", "").replace("$", "").strip())
                .flatMap(value -> {
                    try {
                        return Optional.of(new BigDecimal(value));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    private Optional<ApiSeller> extractSeller(List<String> loreLines, String prefix) {
        return loreLines.stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).strip())
                .filter(value -> !value.isEmpty())
                .findFirst()
                .map(value -> new ApiSeller(Optional.of(value), Optional.empty()));
    }

    private List<String> visibleLore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return List.of();
        }
        return lore.lines().stream()
                .map(value -> value.getString().strip())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private Optional<ApiItemData> parseItemData(ItemStack stack) {
        List<ApiEnchantment> enchantments = parseEnchantments(stack);
        Optional<ApiArmorTrim> trim = parseTrim(stack);
        if (enchantments.isEmpty() && trim.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ApiItemData(enchantments, trim, List.of()));
    }

    private List<ApiEnchantment> parseEnchantments(ItemStack stack) {
        Map<String, Integer> levels = new TreeMap<>();
        addEnchantments(levels, stack.get(DataComponents.ENCHANTMENTS));
        addEnchantments(levels, stack.get(DataComponents.STORED_ENCHANTMENTS));
        List<ApiEnchantment> result = new ArrayList<>();
        levels.forEach((id, level) -> result.add(new ApiEnchantment(id, level)));
        return List.copyOf(result);
    }

    private static void addEnchantments(Map<String, Integer> levels, ItemEnchantments component) {
        if (component == null) {
            return;
        }
        component.entrySet().forEach(entry -> levels.merge(
                entry.getKey().getRegisteredName(), entry.getIntValue(), Math::max
        ));
    }

    private Optional<ApiArmorTrim> parseTrim(ItemStack stack) {
        ArmorTrim value = stack.get(DataComponents.TRIM);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(new ApiArmorTrim(
                Optional.of(value.material().getRegisteredName()),
                Optional.of(value.pattern().getRegisteredName()),
                List.of()
        ));
    }

    private List<ApiContainerItem> parseContents(ItemStack stack) {
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container == null) {
            return List.of();
        }
        List<ApiContainerItem> result = new ArrayList<>();
        container.nonEmptyItemCopyStream().limit(256).forEach(itemStack -> {
            String id = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
            Optional<String> name = Optional.of(itemStack.getHoverName().getString()).filter(s -> !s.isBlank());
            Optional<ApiItemData> data = parseItemData(itemStack);
            result.add(new ApiContainerItem(
                    Optional.of(id),
                    OptionalInt.of(itemStack.getCount()),
                    name,
                    data,
                    List.of()
            ));
        });
        return List.copyOf(result);
    }
}
