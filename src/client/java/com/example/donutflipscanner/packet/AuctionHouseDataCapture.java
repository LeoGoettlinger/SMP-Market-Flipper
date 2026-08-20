package com.example.donutflipscanner.packet;

import com.example.donutflipscanner.api.model.ApiAuctionItem;
import com.example.donutflipscanner.api.model.ApiAuctionListing;
import com.example.donutflipscanner.api.model.ApiAuctionPage;
import com.example.donutflipscanner.api.model.ApiArmorTrim;
import com.example.donutflipscanner.api.model.ApiContainerItem;
import com.example.donutflipscanner.api.model.ApiEnchantment;
import com.example.donutflipscanner.api.model.ApiItemData;
import com.example.donutflipscanner.api.model.ApiPaginationMetadata;
import com.example.donutflipscanner.api.model.ApiSeller;
import com.example.donutflipscanner.automation.model.AuctionInteractionProfile;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.registry.Registries;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Thread-safe capture of auction house screen data intercepted from Minecraft packets.
 * The Mixin pushes data in; PacketMarketScanWork reads it out.
 */
public final class AuctionHouseDataCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("donut_flip_scanner");
    private static final int MAXIMUM_CONTAINER_DEPTH = 4;

    private final AtomicReference<Integer> activeSyncId = new AtomicReference<>(-1);
    private final AtomicReference<String> activeScreenTitle = new AtomicReference<>("");
    private final CopyOnWriteArrayList<ItemStack> capturedSlots = new CopyOnWriteArrayList<>();
    private final AtomicInteger capturedPageCount = new AtomicInteger();
    private final AtomicReference<List<ApiAuctionListing>> lastExtractedListings = new AtomicReference<>(List.of());
    private volatile boolean capturing = false;

    private final Supplier<Optional<AuctionInteractionProfile>> profileSupplier;

    public AuctionHouseDataCapture(Supplier<Optional<AuctionInteractionProfile>> profileSupplier) {
        this.profileSupplier = Objects.requireNonNull(profileSupplier, "profileSupplier");
    }

    /**
     * Called by the Mixin when an OpenScreenS2CPacket arrives.
     * Returns true if this screen matches the configured AH profile.
     */
    public boolean onScreenOpened(int syncId, String screenTitle) {
        Optional<AuctionInteractionProfile> optProfile = profileSupplier.get();
        if (optProfile.isEmpty()) {
            return false;
        }
        AuctionInteractionProfile p = optProfile.orElseThrow();
        if (!p.resultsScreenTitle().equals(screenTitle)) {
            return false;
        }
        activeSyncId.set(syncId);
        activeScreenTitle.set(screenTitle);
        capturedSlots.clear();
        capturing = true;
        capturedPageCount.set(0);
        LOGGER.info("Auction house screen detected (syncId={}, title={})", syncId, screenTitle);
        return true;
    }

    /**
     * Called by the Mixin when an InventoryS2CPacket arrives for the active AH screen.
     * Replaces all captured slots with the new inventory contents.
     */
    public void onInventorySynced(int syncId, List<ItemStack> slots) {
        if (!capturing || activeSyncId.get() != syncId) {
            return;
        }
        capturedSlots.clear();
        capturedSlots.addAll(slots);
        capturedPageCount.incrementAndGet();
        lastExtractedListings.set(extractListings());
    }

    /**
     * Called by the Mixin when a ScreenHandlerSlotUpdateS2CPacket arrives for the active AH screen.
     */
    public void onSlotUpdated(int syncId, int slot, ItemStack stack) {
        if (!capturing || activeSyncId.get() != syncId) {
            return;
        }
        while (capturedSlots.size() <= slot) {
            capturedSlots.add(ItemStack.EMPTY);
        }
        capturedSlots.set(slot, stack);
        lastExtractedListings.set(extractListings());
    }

    /**
     * Called by the Mixin when the screen is closed.
     */
    public void onScreenClosed(int syncId) {
        if (activeSyncId.compareAndSet(syncId, -1)) {
            capturing = false;
            capturedSlots.clear();
            activeScreenTitle.set("");
        }
    }

    /**
     * Returns true if we are currently capturing an AH screen.
     */
    public boolean isCapturing() {
        return capturing;
    }

    /**
     * Returns the number of pages observed during this capture session.
     */
    public int capturedPageCount() {
        return capturedPageCount.get();
    }

    /**
     * Called by PacketMarketScanWork to get the current page as an ApiAuctionPage.
     */
    public Optional<ApiAuctionPage> captureCurrentPage() {
        if (!capturing) {
            return Optional.empty();
        }
        List<ApiAuctionListing> listings = lastExtractedListings.get();
        if (listings.isEmpty()) {
            listings = extractListings();
            lastExtractedListings.set(listings);
        }
        ApiPaginationMetadata pagination = new ApiPaginationMetadata(
                1, listings.size(), OptionalInt.empty(), OptionalInt.empty()
        );
        return Optional.of(new ApiAuctionPage(200, listings, pagination));
    }

    private List<ApiAuctionListing> extractListings() {
        Optional<AuctionInteractionProfile> optProfile = profileSupplier.get();
        if (optProfile.isEmpty()) {
            return List.of();
        }
        AuctionInteractionProfile p = optProfile.orElseThrow();
        List<ItemStack> snapshot = List.copyOf(capturedSlots);
        List<ApiAuctionListing> result = new ArrayList<>();
        int first = Math.max(0, p.firstResultSlot());
        int last = Math.min(snapshot.size() - 1, p.lastResultSlot());
        for (int slotId = first; slotId <= last; slotId++) {
            if (slotId >= snapshot.size()) {
                break;
            }
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
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            int count = stack.getCount();
            Optional<String> displayName = Optional.of(stack.getName().getString()).filter(s -> !s.isBlank());
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
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
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
        addEnchantments(levels, stack.get(DataComponentTypes.ENCHANTMENTS));
        addEnchantments(levels, stack.get(DataComponentTypes.STORED_ENCHANTMENTS));
        List<ApiEnchantment> result = new ArrayList<>();
        levels.forEach((id, level) -> result.add(new ApiEnchantment(id, level)));
        return List.copyOf(result);
    }

    private static void addEnchantments(Map<String, Integer> levels, ItemEnchantmentsComponent component) {
        if (component == null) {
            return;
        }
        component.getEnchantmentEntries().forEach(entry -> levels.merge(
                entry.getKey().getIdAsString(), entry.getIntValue(), Math::max
        ));
    }

    private Optional<ApiArmorTrim> parseTrim(ItemStack stack) {
        ArmorTrim value = stack.get(DataComponentTypes.TRIM);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(new ApiArmorTrim(
                Optional.of(value.material().getIdAsString()),
                Optional.of(value.pattern().getIdAsString()),
                List.of()
        ));
    }

    private List<ApiContainerItem> parseContents(ItemStack stack) {
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) {
            return List.of();
        }
        List<ApiContainerItem> result = new ArrayList<>();
        container.streamNonEmpty().limit(256).forEach(itemStack -> {
            String id = Registries.ITEM.getId(itemStack.getItem()).toString();
            Optional<String> name = Optional.of(itemStack.getName().getString()).filter(s -> !s.isBlank());
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
