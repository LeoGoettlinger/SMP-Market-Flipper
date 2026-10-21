package com.scmarket.market.statistics;

import com.scmarket.market.item.model.NormalizedItem;
import com.scmarket.market.statistics.model.ItemMarketStatistics;

import java.util.concurrent.CompletableFuture;

public interface MarketStatisticsProvider {
    CompletableFuture<ItemMarketStatistics> statisticsFor(
            NormalizedItem item,
            MarketStatisticsConfig config
    );
}
