package com.scmarket.market.statistics;

import com.scmarket.database.DatabaseManager;
import com.scmarket.database.FingerprintRepository;
import com.scmarket.database.ListingRepository;
import com.scmarket.database.SaleRepository;
import com.scmarket.database.entity.ItemFingerprintEntity;
import com.scmarket.market.statistics.model.ItemMarketStatistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

import static com.scmarket.market.statistics.MarketStatisticsTestFixtures.COMMODITY;
import static com.scmarket.market.statistics.MarketStatisticsTestFixtures.NOW;
import static com.scmarket.market.statistics.MarketStatisticsTestFixtures.listing;
import static com.scmarket.market.statistics.MarketStatisticsTestFixtures.sale;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryMarketStatisticsServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void asynchronouslyCombinesBoundedSaleAndAskSnapshots() {
        try (DatabaseManager database = new DatabaseManager(temporaryDirectory.resolve("market-data.db"))) {
            database.ready().join();
            FingerprintRepository fingerprints = new FingerprintRepository(database);
            SaleRepository sales = new SaleRepository(database);
            ListingRepository listings = new ListingRepository(database);
            fingerprints.insertIfAbsent(new ItemFingerprintEntity(
                    COMMODITY.fingerprint().sha256(),
                    COMMODITY.itemId(),
                    COMMODITY.matchQuality().matchType().name(),
                    COMMODITY.fingerprint().canonicalMetadata(),
                    NOW
            )).join();
            sales.insertBatch(List.of(
                    sale("sale-1", new BigDecimal("400"), 4, Duration.ofHours(1)),
                    sale("sale-2", new BigDecimal("600"), 4, Duration.ofHours(2))
            )).join();
            listings.upsertBatch(List.of(
                    listing("ask-1", new BigDecimal("360"), 4, "seller-a",
                            com.scmarket.database.entity.ListingState.ACTIVE)
            )).join();
            RepositoryMarketStatisticsService service = new RepositoryMarketStatisticsService(
                    sales,
                    listings,
                    new MarketStatisticsCalculator(),
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

            ItemMarketStatistics result = service.statisticsFor(
                    COMMODITY, MarketStatisticsTestFixtures.config(2, 8)
            ).join();

            assertEquals(2, result.comparableSaleCount());
            assertEquals(0, new BigDecimal("125").compareTo(result.median().orElseThrow()));
            assertEquals(1, result.activeAsks().listingCount());
            assertEquals(0, new BigDecimal("90").compareTo(result.activeAsks().lowestAsk().orElseThrow()));
        }
    }
}
