package com.scmarket.configuration;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Small, atomic, local-only persistence for Order→AH settings. */
public final class OrderModuleSettingsStore {
    private static final int MAXIMUM_FILE_BYTES = 128 * 1024;
    private final Path file;
    private OrderModuleSettings current;

    public OrderModuleSettingsStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.current = OrderModuleSettings.defaults();
    }

    public synchronized OrderModuleSettings load() throws IOException {
        if (!Files.exists(file)) {
            current = OrderModuleSettings.defaults();
            return current;
        }
        long size = Files.size(file);
        if (size > MAXIMUM_FILE_BYTES) {
            throw new IOException("Order settings file exceeds the size limit");
        }
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        try {
            Set<String> whitelist = parseItems(values.getProperty("itemWhitelist", ""));
            current = new OrderModuleSettings(
                    whitelist,
                    Boolean.parseBoolean(values.getProperty("whitelistOnly", Boolean.toString(!whitelist.isEmpty()))),
                    integer(values, "orderQuantity", OrderModuleSettings.DEFAULT_ORDER_QUANTITY),
                    integer(values, "maximumTotalOrders", OrderModuleSettings.MAXIMUM_TOTAL_ORDERS),
                    integer(values, "maximumOrdersPerItem", OrderModuleSettings.MAXIMUM_ORDERS_PER_ITEM),
                    new BigDecimal(values.getProperty("minimumProfitPerItem", "1")),
                    Boolean.parseBoolean(values.getProperty("localActionRecording", "false")),
                    integer(values, "maximumRecordedActions",
                            OrderModuleSettings.DEFAULT_MAXIMUM_RECORDED_ACTIONS)
            );
            return current;
        } catch (RuntimeException malformed) {
            throw new IOException("Order settings contain an invalid value", malformed);
        }
    }

    public synchronized OrderModuleSettings current() {
        return current;
    }

    public synchronized OrderModuleSettings update(UnaryOperator<OrderModuleSettings> change) throws IOException {
        Objects.requireNonNull(change, "change");
        OrderModuleSettings updated = Objects.requireNonNull(change.apply(current), "updated settings");
        save(updated);
        current = updated;
        return updated;
    }

    public synchronized void save(OrderModuleSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties values = new Properties();
        values.setProperty("itemWhitelist", String.join(",", settings.itemWhitelist().stream().sorted().toList()));
        values.setProperty("whitelistOnly", Boolean.toString(settings.whitelistOnly()));
        values.setProperty("orderQuantity",  Integer.toString(settings.orderQuantity()));
        values.setProperty("maximumTotalOrders", Integer.toString(settings.maximumTotalOrders()));
        values.setProperty("maximumOrdersPerItem", Integer.toString(settings.maximumOrdersPerItem()));
        values.setProperty("minimumProfitPerItem", settings.minimumProfitPerItem().stripTrailingZeros().toPlainString());
        values.setProperty("localActionRecording", Boolean.toString(settings.localActionRecording()));
        values.setProperty("maximumRecordedActions", Integer.toString(settings.maximumRecordedActions()));

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            values.store(writer, "SC-Market Order-to-AH settings; local only");
        }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int integer(Properties values, String name, int defaultValue) {
        String value = values.getProperty(name);
        return value == null ? defaultValue : Integer.parseInt(value.strip());
    }

    private static Set<String> parseItems(String raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw.isBlank()) {
            return Set.of();
        }
        for (String item : raw.split(",", -1)) {
            if (item.isBlank()) {
                throw new IllegalArgumentException("whitelist contains an empty item ID");
            }
            values.add(item.strip());
        }
        return Set.copyOf(values);
    }
}
