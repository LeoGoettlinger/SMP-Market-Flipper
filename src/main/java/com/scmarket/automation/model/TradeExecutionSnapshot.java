package com.scmarket.automation.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Immutable view of execution status; server access is evaluated from the current exact allowlist. */
public record TradeExecutionSnapshot(
        boolean emergencyStopped,
        int purchasesThisSession,
        BigDecimal openExposure,
        Optional<String> activeExecutionId,
        Optional<TradeExecutionState> activeState,
        String statusMessage
) {
    public TradeExecutionSnapshot {
        activeExecutionId = Objects.requireNonNullElse(activeExecutionId, Optional.empty());
        activeState = Objects.requireNonNullElse(activeState, Optional.empty());
        Objects.requireNonNull(openExposure, "openExposure");
        statusMessage = Objects.requireNonNullElse(statusMessage, "");
    }

    public static TradeExecutionSnapshot idle() {
        return new TradeExecutionSnapshot(false, 0, BigDecimal.ZERO,
                Optional.empty(), Optional.empty(), "Automation is idle.");
    }
}
