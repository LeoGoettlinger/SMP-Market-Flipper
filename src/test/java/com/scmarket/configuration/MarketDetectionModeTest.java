package com.scmarket.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDetectionModeTest {
    @Test
    void missingApiCredentialFallsBackToPacketScan() {
        assertEquals(MarketDetectionMode.PACKET_SCAN,
                MarketDetectionMode.API.availableWhen(false));
    }

    @Test
    void packetScanNeverRequiresAnApiCredential() {
        assertEquals(MarketDetectionMode.PACKET_SCAN,
                MarketDetectionMode.PACKET_SCAN.availableWhen(false));
    }

    @Test
    void configuredApiModeIsPreserved() {
        assertEquals(MarketDetectionMode.API,
                MarketDetectionMode.API.availableWhen(true));
    }
}
