package com.scmarket.data;

import com.scmarket.automation.model.TradeExecutionSnapshot;
import com.scmarket.balance.BalanceSnapshot;
import com.scmarket.configuration.AutomationConfig;

import java.util.Objects;

public record AutomationSettingsSnapshot(
        AutomationConfig configuration,
        TradeExecutionSnapshot execution,
        BalanceSnapshot balance,
        String currentServer,
        String warning
) {
    public AutomationSettingsSnapshot {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(balance, "balance");
        currentServer = Objects.requireNonNullElse(currentServer, "Not connected");
        warning = Objects.requireNonNullElse(warning, "");
    }
}
