package com.example.donutflipscanner.configuration;

import java.util.Objects;

/** Runtime switches are separate from scanner configuration so changes can apply immediately. */
public record ModRuntimeSettings(boolean enabled, OperatingMode mode) {
    public ModRuntimeSettings {
        Objects.requireNonNull(mode, "mode");
    }

    public static ModRuntimeSettings defaults() {
        return new ModRuntimeSettings(true, OperatingMode.FLIPPER);
    }
}
