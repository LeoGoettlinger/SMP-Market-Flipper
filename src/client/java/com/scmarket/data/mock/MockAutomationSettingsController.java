package com.scmarket.data.mock;

import com.scmarket.automation.service.TradeAutomationCoordinator;
import com.scmarket.configuration.AutomationConfig;
import com.scmarket.data.provider.ClientAutomationSettingsController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class MockAutomationSettingsController extends ClientAutomationSettingsController {
    private static AtomicReference<AutomationConfig> configuration() {
        return new AtomicReference<>(AutomationConfig.defaults());
    }

    public MockAutomationSettingsController() {
        this(configuration());
    }

    private MockAutomationSettingsController(AtomicReference<AutomationConfig> configuration) {
        super(configuration::get, updated -> {
            configuration.set(updated);
            return CompletableFuture.completedFuture(null);
        }, () -> "", new TradeAutomationCoordinator(configuration::get));
    }
}
