package com.scmarket.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModRuntimeSettingsStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsGlobalEnableAndMultipleIndependentModules() {
        ModRuntimeSettingsStore store = new ModRuntimeSettingsStore(
                temporaryDirectory.resolve("runtime-settings.txt"));
        ModRuntimeSettings expected = new ModRuntimeSettings(false,
                java.util.Set.of(MarketModule.FISHING, MarketModule.ORDER_TO_AH));

        store.save(expected);

        assertEquals(expected, store.load());
    }

    @Test
    void migratesThePreviousSingleModeFormat() throws Exception {
        java.nio.file.Path path = temporaryDirectory.resolve("runtime-settings.txt");
        java.nio.file.Files.writeString(path, "enabled=true\nmode=FISHING\n");

        assertEquals(new ModRuntimeSettings(true, OperatingMode.FISHING),
                new ModRuntimeSettingsStore(path).load());
    }
}
