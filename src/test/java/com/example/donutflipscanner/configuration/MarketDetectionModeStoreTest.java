package com.example.donutflipscanner.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDetectionModeStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndLoadsExplicitMode() {
        MarketDetectionModeStore store = new MarketDetectionModeStore(
                temporaryDirectory.resolve("market-detection-mode.txt"));

        assertEquals(MarketDetectionMode.API, store.load(MarketDetectionMode.API));
        store.save(MarketDetectionMode.PACKET_SCAN);
        assertEquals(MarketDetectionMode.PACKET_SCAN, store.load(MarketDetectionMode.API));
        store.save(MarketDetectionMode.API);
        assertEquals(MarketDetectionMode.API, store.load(MarketDetectionMode.PACKET_SCAN));
    }

    @Test
    void invalidPreferenceFallsBackToSuppliedDefault() throws Exception {
        Path path = temporaryDirectory.resolve("market-detection-mode.txt");
        Files.writeString(path, "not-a-mode");

        assertEquals(MarketDetectionMode.PACKET_SCAN,
                new MarketDetectionModeStore(path).load(MarketDetectionMode.PACKET_SCAN));
    }
}
