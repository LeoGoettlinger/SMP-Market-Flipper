package com.scmarket.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderModuleSettingsStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsKeepThePreviouslyAgreedLimitsAndRecordingOff() {
        OrderModuleSettings defaults = OrderModuleSettings.defaults();

        assertEquals(1_024, defaults.orderQuantity());
        assertEquals(10, defaults.maximumTotalOrders());
        assertEquals(2, defaults.maximumOrdersPerItem());
        assertEquals(BigDecimal.ONE, defaults.minimumProfitPerItem());
        assertFalse(defaults.localActionRecording());
        assertTrue(defaults.allowsItem("minecraft:diamond"));
        assertFalse(defaults.withWhitelistOnly(true).allowsItem("minecraft:diamond"));
    }

    @Test
    void settingsRoundTripAndWhitelistRestrictsTrades() throws Exception {
        Path file = temporaryDirectory.resolve("order-module.properties");
        OrderModuleSettingsStore first = new OrderModuleSettingsStore(file);
        first.load();
        first.update(settings -> new OrderModuleSettings(Set.of("minecraft:diamond"), 2_048,
                6, 1, new BigDecimal("5"), true, 2_500));

        OrderModuleSettings loaded = new OrderModuleSettingsStore(file).load();
        assertEquals(2_048, loaded.orderQuantity());
        assertEquals(6, loaded.maximumTotalOrders());
        assertEquals(1, loaded.maximumOrdersPerItem());
        assertEquals(new BigDecimal("5"), loaded.minimumProfitPerItem());
        assertEquals(Set.of("minecraft:diamond"), loaded.itemWhitelist());
        assertTrue(loaded.allowsItem("minecraft:diamond"));
        assertFalse(loaded.allowsItem("minecraft:emerald"));
        assertTrue(loaded.localActionRecording());
        assertEquals(2_500, loaded.maximumRecordedActions());
    }

    @Test
    void settingsCannotExceedServerObservedOrderCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new OrderModuleSettings(
                Set.of(), 1_024, 11, 2, BigDecimal.ONE, false, 5_000));
        assertThrows(IllegalArgumentException.class, () -> new OrderModuleSettings(
                Set.of(), 1_024, 10, 3, BigDecimal.ONE, false, 5_000));
    }
}
