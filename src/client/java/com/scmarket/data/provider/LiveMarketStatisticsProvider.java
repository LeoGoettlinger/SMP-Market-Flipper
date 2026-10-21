package com.scmarket.data.provider;

import com.scmarket.data.MarketStatistics;
import com.scmarket.provider.LiveMarketSnapshot;
import com.scmarket.provider.LiveMarketSnapshotService;

import java.util.Objects;

public final class LiveMarketStatisticsProvider implements MarketStatisticsProvider {
    private final LiveMarketSnapshotService snapshots;

    public LiveMarketStatisticsProvider(LiveMarketSnapshotService snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    @Override
    public MarketStatistics getMarketStatistics() {
        LiveMarketSnapshot value = snapshots.snapshot();
        int opportunities = value.activeOpportunityCount() > Integer.MAX_VALUE
                ? Integer.MAX_VALUE : (int) value.activeOpportunityCount();
        return new MarketStatistics(
                value.activeListings(), value.storedTransactions(), opportunities,
                value.databaseOpportunityCount(), ClientDataFormat.saturatedLong(value.combinedPotentialProfit()),
                value.databaseAvailable()
        );
    }
}
