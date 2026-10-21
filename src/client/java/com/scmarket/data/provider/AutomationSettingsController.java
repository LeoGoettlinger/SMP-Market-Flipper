package com.scmarket.data.provider;

import com.scmarket.automation.model.AutomationMode;
import com.scmarket.data.AutomationSettingsSnapshot;
import com.scmarket.data.FlipOpportunity;
import com.scmarket.data.OpportunityActionResult;

import java.util.concurrent.CompletableFuture;

public interface AutomationSettingsController {
    AutomationSettingsSnapshot snapshot();

    CompletableFuture<OpportunityActionResult> runDryRun(FlipOpportunity opportunity);

    CompletableFuture<OpportunityActionResult> executeConfiguredMode(FlipOpportunity opportunity);

    CompletableFuture<OpportunityActionResult> confirmPending();

    CompletableFuture<Void> setEnabled(boolean enabled);

    CompletableFuture<Void> setMode(AutomationMode mode);

    CompletableFuture<Void> setActionDelayMillis(long delayMillis);

    CompletableFuture<Void> allowCurrentServer();

    boolean armCurrentServer(String exactConfirmation);

    void disarm();

    void emergencyStop();

    void clearEmergencyStop();
}
