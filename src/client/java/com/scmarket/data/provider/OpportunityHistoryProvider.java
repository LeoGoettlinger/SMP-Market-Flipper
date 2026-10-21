package com.scmarket.data.provider;

import com.scmarket.data.OpportunityHistoryEntry;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OpportunityHistoryProvider {
    List<OpportunityHistoryEntry> getHistory();

    default CompletableFuture<Integer> clearHistory() {
        return CompletableFuture.completedFuture(0);
    }
}
