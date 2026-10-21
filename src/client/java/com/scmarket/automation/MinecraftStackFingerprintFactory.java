package com.scmarket.automation;

import com.scmarket.automation.model.AuctionInteractionProfile;
import com.scmarket.market.item.ItemNormalizer;
import com.scmarket.market.item.SafeItemCategoryRegistry;
import com.scmarket.market.item.model.ArmorTrimDescriptor;
import com.scmarket.market.item.model.ItemDescriptor;
import com.scmarket.market.item.model.ItemEnchantmentDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/** Converts an in-game stack into the same provider-neutral fingerprint used for API items. */
public final class MinecraftStackFingerprintFactory {
    private static final int MAXIMUM_CONTAINER_DEPTH = 4;

    private final ItemNormalizer normalizer = new ItemNormalizer(
            SafeItemCategoryRegistry.liveDefaults(), ItemNormalizer.DEFAULT_MAXIMUM_CONTAINER_DEPTH
    );

    public String fingerprint(ItemStack stack, AuctionInteractionProfile profile) {
        return normalize(stack, profile).fingerprint().sha256();
    }

    public com.scmarket.market.item.model.NormalizedItem normalize(
            ItemStack stack, AuctionInteractionProfile profile
    ) {
        return normalizer.normalize(descriptor(stack, profile, 0));
    }

    List<String> visibleLore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines().stream()
                .map(value -> value.getString().strip()).toList();
    }

    ItemStack intrinsicCopy(ItemStack stack, AuctionInteractionProfile profile) {
        ItemStack copy = stack.copyWithCount(1);
        ItemLore lore = copy.get(DataComponents.LORE);
        if (lore == null) {
            return copy;
        }
        List<Component> intrinsic = lore.lines().stream()
                .filter(line -> !ignored(line.getString().strip(), profile))
                .toList();
        if (intrinsic.isEmpty()) {
            copy.remove(DataComponents.LORE);
        } else {
            copy.set(DataComponents.LORE, new ItemLore(intrinsic));
        }
        return copy;
    }

    private ItemDescriptor descriptor(
            ItemStack stack, AuctionInteractionProfile profile, int depth
    ) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Optional<String> customName = Optional.ofNullable(stack.get(DataComponents.CUSTOM_NAME))
                .map(Component::getString);
        List<String> intrinsicLore = visibleLore(stack).stream()
                .filter(line -> !ignored(line, profile)).toList();
        List<ItemDescriptor> contents = depth >= MAXIMUM_CONTAINER_DEPTH
                ? List.of() : contents(stack, profile, depth);
        return new ItemDescriptor(
                Optional.of(itemId), OptionalInt.of(stack.getCount()), customName, intrinsicLore,
                enchantments(stack), trim(stack), contents, List.of()
        );
    }

    private List<ItemDescriptor> contents(
            ItemStack stack, AuctionInteractionProfile profile, int depth
    ) {
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container == null) {
            return List.of();
        }
        return container.nonEmptyStream().limit(256)
                .map(value -> descriptor(value, profile, depth + 1)).toList();
    }

    private static List<ItemEnchantmentDescriptor> enchantments(ItemStack stack) {
        Map<String, Integer> levels = new TreeMap<>();
        addEnchantments(levels, stack.get(DataComponents.ENCHANTMENTS));
        addEnchantments(levels, stack.get(DataComponents.STORED_ENCHANTMENTS));
        List<ItemEnchantmentDescriptor> result = new ArrayList<>();
        levels.forEach((id, level) -> result.add(new ItemEnchantmentDescriptor(id, level)));
        return List.copyOf(result);
    }

    private static void addEnchantments(
            Map<String, Integer> levels, ItemEnchantments component
    ) {
        if (component == null) {
            return;
        }
        component.entrySet().forEach(entry -> levels.merge(
                entry.getKey().getRegisteredName(), entry.getIntValue(), Math::max
        ));
    }

    private static Optional<ArmorTrimDescriptor> trim(ItemStack stack) {
        ArmorTrim value = stack.get(DataComponents.TRIM);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(new ArmorTrimDescriptor(
                Optional.of(value.material().getRegisteredName()),
                Optional.of(value.pattern().getRegisteredName()),
                List.of()
        ));
    }

    private static boolean ignored(String line, AuctionInteractionProfile profile) {
        if (!profile.listingKeyLorePrefix().isBlank()
                && line.startsWith(profile.listingKeyLorePrefix())) {
            return true;
        }
        if (line.startsWith(profile.priceLorePrefix())
                || line.startsWith(profile.sellerLorePrefix())) {
            return true;
        }
        return profile.ignoredLorePrefixes().stream().anyMatch(line::startsWith);
    }
}
