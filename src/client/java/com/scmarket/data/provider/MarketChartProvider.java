package com.scmarket.data.provider;

import com.scmarket.data.MarketChartSnapshot;

@FunctionalInterface
public interface MarketChartProvider {
    MarketChartSnapshot getMarketChart();
}
