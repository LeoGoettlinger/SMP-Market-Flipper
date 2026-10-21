package com.scmarket.configuration;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** User-editable Order→AH limits. Safety invariants such as full-order-only pickup stay non-configurable. */
public record OrderModuleSettings(
        Set<String> itemWhitelist,
        boolean whitelistOnly,
        int orderQuantity,
        int maximumTotalOrders,
        int maximumOrdersPerItem,
        BigDecimal minimumProfitPerItem,
        boolean localActionRecording,
        int maximumRecordedActions
) {
    public static final int DEFAULT_ORDER_QUANTITY = 1_024;
    public static final int MAXIMUM_SUPPORTED_ORDER_QUANTITY = 2_048;
    public static final int MAXIMUM_TOTAL_ORDERS = 10;
    public static final int MAXIMUM_ORDERS_PER_ITEM = 2;
    public static final int MAXIMUM_WHITELIST_ITEMS = 256;
    public static final int DEFAULT_MAXIMUM_RECORDED_ACTIONS = 5_000;
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public OrderModuleSettings {
        TreeSet<String> normalized = new TreeSet<>();
        for (String id : Objects.requireNonNull(itemWhitelist, "itemWhitelist")) {
            String value = Objects.requireNonNull(id, "item id").strip().toLowerCase(java.util.Locale.ROOT);
            if (value.length() > 256 || !ITEM_ID.matcher(value).matches()) {
                throw new IllegalArgumentException("invalid namespaced item ID: " + value);
            }
            normalized.add(value);
        }
        if (normalized.size() > MAXIMUM_WHITELIST_ITEMS) {
            throw new IllegalArgumentException("item whitelist exceeds " + MAXIMUM_WHITELIST_ITEMS + " entries");
        }
        itemWhitelist = Set.copyOf(normalized);
        if (orderQuantity < 1 || orderQuantity > MAXIMUM_SUPPORTED_ORDER_QUANTITY) {
            throw new IllegalArgumentException("orderQuantity must be between 1 and 2048");
        }
        if (maximumTotalOrders < 1 || maximumTotalOrders > MAXIMUM_TOTAL_ORDERS) {
            throw new IllegalArgumentException("maximumTotalOrders must be between 1 and 10");
        }
        if (maximumOrdersPerItem < 1 || maximumOrdersPerItem > MAXIMUM_ORDERS_PER_ITEM) {
            throw new IllegalArgumentException("maximumOrdersPerItem must be between 1 and 2");
        }
        minimumProfitPerItem = Objects.requireNonNull(minimumProfitPerItem, "minimumProfitPerItem");
        if (minimumProfitPerItem.compareTo(BigDecimal.ONE) < 0
                || minimumProfitPerItem.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("minimumProfitPerItem must be between 1 and 1000000");
        }
        if (maximumRecordedActions < 100 || maximumRecordedActions > 10_000) {
            throw new IllegalArgumentException("maximumRecordedActions must be between 100 and 10000");
        }
    }

    /** Source compatibility for tests/integrations predating the explicit whitelist switch. */
    public OrderModuleSettings(
            Set<String> itemWhitelist, int orderQuantity, int maximumTotalOrders, int maximumOrdersPerItem,
            BigDecimal minimumProfitPerItem, boolean localActionRecording, int maximumRecordedActions
    ) {
        this(itemWhitelist, !Objects.requireNonNull(itemWhitelist, "itemWhitelist").isEmpty(), orderQuantity,
                maximumTotalOrders, maximumOrdersPerItem, minimumProfitPerItem, localActionRecording,
                maximumRecordedActions);
    }

    public static OrderModuleSettings defaults() {
        return new OrderModuleSettings(Set.of(), false, DEFAULT_ORDER_QUANTITY, MAXIMUM_TOTAL_ORDERS,
                MAXIMUM_ORDERS_PER_ITEM, BigDecimal.ONE, false, DEFAULT_MAXIMUM_RECORDED_ACTIONS);
    }

    /** When whitelist-only mode is active, even an empty list allows no trades. */
    public boolean allowsItem(String itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return !whitelistOnly || itemWhitelist.contains(itemId.toLowerCase(java.util.Locale.ROOT));
    }

    public OrderModuleSettings withWhitelistOnly(boolean enabled) {
        return new OrderModuleSettings(itemWhitelist, enabled, orderQuantity, maximumTotalOrders,
                maximumOrdersPerItem, minimumProfitPerItem, localActionRecording, maximumRecordedActions);
    }

    public OrderModuleSettings withWhitelistItem(String itemId) {
        TreeSet<String> updated = new TreeSet<>(itemWhitelist);
        updated.add(Objects.requireNonNull(itemId, "itemId"));
        return new OrderModuleSettings(updated, true, orderQuantity, maximumTotalOrders, maximumOrdersPerItem,
                minimumProfitPerItem, localActionRecording, maximumRecordedActions);
    }

    public OrderModuleSettings withoutWhitelistItem(String itemId) {
        TreeSet<String> updated = new TreeSet<>(itemWhitelist);
        updated.remove(Objects.requireNonNull(itemId, "itemId").toLowerCase(java.util.Locale.ROOT));
        return new OrderModuleSettings(updated, whitelistOnly, orderQuantity, maximumTotalOrders,
                maximumOrdersPerItem, minimumProfitPerItem, localActionRecording, maximumRecordedActions);
    }
}
