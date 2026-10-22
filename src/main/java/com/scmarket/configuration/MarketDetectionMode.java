package com.scmarket.configuration;

/** Selects where live auction data comes from; packet capture never issues server commands itself. */
public enum MarketDetectionMode {
    API,
    PACKET_SCAN;

    /** Packet scanning is the safe no-credential fallback when an old preference requests API mode. */
    public MarketDetectionMode availableWhen(boolean apiCredentialConfigured) {
        return this == API && !apiCredentialConfigured ? PACKET_SCAN : this;
    }
}
