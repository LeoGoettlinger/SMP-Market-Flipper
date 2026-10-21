package com.scmarket.data.mock;

import com.scmarket.data.MarketStatistics;
import com.scmarket.data.provider.MarketStatisticsProvider;
import com.scmarket.data.provider.OpportunityProvider;

import java.util.Objects;

public final class MockMarketStatisticsProvider implements MarketStatisticsProvider {
    private final OpportunityProvider opportunityProvider;

    public MockMarketStatisticsProvider(OpportunityProvider opportunityProvider) {
        this.opportunityProvider = Objects.requireNonNull(opportunityProvider, "opportunityProvider");
    }

    @Override
    public MarketStatistics getMarketStatistics() {
        return new MarketStatistics(12_482, 87_310, opportunityProvider.getOpportunities().size());
    }
}

