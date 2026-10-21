package com.scmarket.market.statistics;

import com.scmarket.database.ListingRepository;
import com.scmarket.database.SaleRepository;
import com.scmarket.diagnostics.PerformanceMetrics;
import com.scmarket.diagnostics.PerformanceOperation;
import com.scmarket.market.item.model.NormalizedItem;
import com.scmarket.market.statistics.model.ItemMarketStatistics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronously composes bounded repository snapshots. The pure calculator is
 * never run on Minecraft's render thread by this service.
 */
public final class RepositoryMarketStatisticsService implements MarketStatisticsProvider {
    private static final Duration ACTIVE_ASK_FRESHNESS = Duration.ofMinutes(10);
    private final SaleRepository saleRepository;
    private final ListingRepository listingRepository;
    private final MarketStatisticsCalculator calculator;
    private final Clock clock;
    private final PerformanceMetrics performanceMetrics;

    public RepositoryMarketStatisticsService(
            SaleRepository saleRepository,
            ListingRepository listingRepository
    ) {
        this(saleRepository, listingRepository, new MarketStatisticsCalculator(), Clock.systemUTC(),
                new PerformanceMetrics());
    }

    public RepositoryMarketStatisticsService(
            SaleRepository saleRepository,
            ListingRepository listingRepository,
            MarketStatisticsCalculator calculator,
            Clock clock
    ) {
        this(saleRepository, listingRepository, calculator, clock, new PerformanceMetrics());
    }

    public RepositoryMarketStatisticsService(
            SaleRepository saleRepository,
            ListingRepository listingRepository,
            MarketStatisticsCalculator calculator,
            Clock clock,
            PerformanceMetrics performanceMetrics
    ) {
        this.saleRepository = Objects.requireNonNull(saleRepository, "saleRepository");
        this.listingRepository = Objects.requireNonNull(listingRepository, "listingRepository");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.performanceMetrics = Objects.requireNonNull(performanceMetrics, "performanceMetrics");
    }

    @Override
    public CompletableFuture<ItemMarketStatistics> statisticsFor(
            NormalizedItem item,
            MarketStatisticsConfig config
    ) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(config, "config");
        Instant snapshotTime = clock.instant();
        String fingerprint = item.fingerprint().sha256();
        return performanceMetrics.measureAsync(PerformanceOperation.COMPARABLE_SALE_QUERY, () -> {
            var sales = saleRepository.findByFingerprintBetween(
                    fingerprint,
                    config.lookback().cutoff(snapshotTime),
                    snapshotTime,
                    config.maxComparableSales()
            );
            var asks = listingRepository.findActiveByFingerprintSince(
                    fingerprint,
                    config.maxActiveListings(),
                    snapshotTime.minus(ACTIVE_ASK_FRESHNESS)
            );
            return sales.thenCombine(asks, (saleSnapshot, askSnapshot) -> calculator.calculate(
                    item, saleSnapshot, askSnapshot, config, snapshotTime
            ));
        });
    }
}
