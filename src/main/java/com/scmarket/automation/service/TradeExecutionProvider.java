package com.scmarket.automation.service;

import com.scmarket.automation.model.TradeExecutionRequest;
import com.scmarket.automation.model.TradeExecutionResult;
import com.scmarket.automation.model.TradeExecutionSnapshot;

import java.util.concurrent.CompletableFuture;

public interface TradeExecutionProvider {
    CompletableFuture<TradeExecutionResult> submit(TradeExecutionRequest request);

    CompletableFuture<Boolean> cancel(String executionId);

    TradeExecutionSnapshot snapshot();

    void emergencyStop(String reason);
}
