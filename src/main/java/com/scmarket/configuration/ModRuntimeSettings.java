package com.scmarket.configuration;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Runtime switches are independent and persist separately from scanner configuration. */
public record ModRuntimeSettings(boolean enabled, Set<MarketModule> activeModules) {
    public ModRuntimeSettings {
        Objects.requireNonNull(activeModules, "activeModules");
        activeModules = activeModules.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(activeModules));
    }

    /** Compatibility constructor for old settings/UI integrations that selected one workflow. */
    public ModRuntimeSettings(boolean enabled, OperatingMode mode) {
        this(enabled, switch (Objects.requireNonNull(mode, "mode")) {
            case FLIPPER -> Set.of(MarketModule.AH_DEALS);
            case FISHING -> Set.of(MarketModule.FISHING);
        });
    }

    public static ModRuntimeSettings defaults() {
        return new ModRuntimeSettings(true, Set.of(MarketModule.AH_DEALS));
    }

    public boolean moduleEnabled(MarketModule module) {
        return activeModules.contains(Objects.requireNonNull(module, "module"));
    }

    public ModRuntimeSettings withModule(MarketModule module, boolean moduleEnabled) {
        EnumSet<MarketModule> updated = activeModules.isEmpty()
                ? EnumSet.noneOf(MarketModule.class) : EnumSet.copyOf(activeModules);
        if (moduleEnabled) {
            updated.add(Objects.requireNonNull(module, "module"));
        } else {
            updated.remove(Objects.requireNonNull(module, "module"));
        }
        return new ModRuntimeSettings(enabled, updated);
    }

    public ModRuntimeSettings withEnabled(boolean globallyEnabled) {
        return new ModRuntimeSettings(globallyEnabled, activeModules);
    }

    /** Legacy projection for older callers; new code should query moduleEnabled(). */
    @Deprecated
    public OperatingMode mode() {
        return moduleEnabled(MarketModule.FISHING) && !moduleEnabled(MarketModule.AH_DEALS)
                && !moduleEnabled(MarketModule.ORDER_TO_AH)
                ? OperatingMode.FISHING : OperatingMode.FLIPPER;
    }
}
