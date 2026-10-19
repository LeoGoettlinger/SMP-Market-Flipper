package com.example.donutflipscanner.configuration;

/** Selects where live auction data comes from; packet capture never issues server commands itself. */
public enum MarketDetectionMode {
    API,
    PACKET_SCAN
}
