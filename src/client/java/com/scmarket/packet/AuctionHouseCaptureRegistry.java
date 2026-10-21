package com.scmarket.packet;

import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe bridge from packet-listener mixins to the active scanner runtime. */
public final class AuctionHouseCaptureRegistry {
    private static final AtomicReference<AuctionHouseDataCapture> ACTIVE = new AtomicReference<>();

    private AuctionHouseCaptureRegistry() {
    }

    public static void install(AuctionHouseDataCapture capture) {
        ACTIVE.set(capture);
    }

    public static AuctionHouseDataCapture current() {
        return ACTIVE.get();
    }

    public static void clear(AuctionHouseDataCapture capture) {
        ACTIVE.compareAndSet(capture, null);
    }
}
