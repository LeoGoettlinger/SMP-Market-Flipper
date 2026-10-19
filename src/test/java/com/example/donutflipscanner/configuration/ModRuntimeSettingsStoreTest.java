package com.example.donutflipscanner.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModRuntimeSettingsStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsGlobalEnableAndOperatingMode() {
        ModRuntimeSettingsStore store = new ModRuntimeSettingsStore(
                temporaryDirectory.resolve("runtime-settings.txt"));
        ModRuntimeSettings expected = new ModRuntimeSettings(false, OperatingMode.FISHING);

        store.save(expected);

        assertEquals(expected, store.load());
    }
}
