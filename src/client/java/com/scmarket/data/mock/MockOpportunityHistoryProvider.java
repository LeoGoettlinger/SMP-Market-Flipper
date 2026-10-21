package com.scmarket.data.mock;

import com.scmarket.data.OpportunityHistoryEntry;
import com.scmarket.data.provider.OpportunityHistoryProvider;

import java.util.List;

public final class MockOpportunityHistoryProvider implements OpportunityHistoryProvider {
    private static final List<OpportunityHistoryEntry> HISTORY = List.of(
            new OpportunityHistoryEntry("mock-history-001", "Diamond Block", "REVIEWED"),
            new OpportunityHistoryEntry("mock-history-002", "Emerald Block", "EXPIRED")
    );

    @Override
    public List<OpportunityHistoryEntry> getHistory() {
        return HISTORY;
    }
}

