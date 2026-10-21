package com.scmarket.integration;

import com.scmarket.api.ApiClientConfig;
import com.scmarket.api.ApiHttpResponse;
import com.scmarket.api.ApiHttpTransport;
import com.scmarket.api.ApiRequestScheduler;
import com.scmarket.api.ApiRetryPolicy;
import com.scmarket.api.DonutApiClient;
import com.scmarket.api.model.ApiAuctionPage;
import com.scmarket.api.model.ApiTransactionPage;
import com.scmarket.database.DatabaseManager;
import com.scmarket.database.FingerprintRepository;
import com.scmarket.database.ListingRepository;
import com.scmarket.database.OpportunityRepository;
import com.scmarket.database.SaleRepository;
import com.scmarket.database.entity.ListingEntity;
import com.scmarket.diagnostics.PerformanceMetrics;
import com.scmarket.diagnostics.CacheStatistics;
import com.scmarket.diagnostics.PerformanceOperation;
import com.scmarket.diagnostics.PerformanceSnapshot;
import com.scmarket.market.confidence.ConfidenceConfig;
import com.scmarket.market.item.ApiItemDescriptorMapper;
import com.scmarket.market.item.ItemNormalizer;
import com.scmarket.market.item.model.NormalizedItem;
import com.scmarket.market.opportunity.ItemFilterPolicy;
import com.scmarket.market.opportunity.OpportunityEvaluation;
import com.scmarket.market.opportunity.OpportunityEvaluationConfig;
import com.scmarket.market.opportunity.OpportunityEvaluationRequest;
import com.scmarket.market.opportunity.OpportunityEvaluator;
import com.scmarket.market.opportunity.OpportunityPersistenceMapper;
import com.scmarket.market.opportunity.OpportunityThresholds;
import com.scmarket.market.opportunity.SupportedItemPolicy;
import com.scmarket.market.profit.ProfitEvaluationConfig;
import com.scmarket.market.risk.ManipulationRiskConfig;
import com.scmarket.market.risk.MarketRiskLevel;
import com.scmarket.market.statistics.MarketLookbackPeriod;
import com.scmarket.market.statistics.MarketStatisticsCalculator;
import com.scmarket.market.statistics.MarketStatisticsConfig;
import com.scmarket.market.statistics.RecencyWeightPolicy;
import com.scmarket.market.statistics.RepositoryMarketStatisticsService;
import com.scmarket.market.statistics.model.ItemMarketStatistics;
import com.scmarket.market.value.FairValueConfig;
import com.scmarket.market.value.FairValueMarketContext;
import com.scmarket.provider.LiveMarketSnapshot;
import com.scmarket.provider.LiveMarketSnapshotService;
import com.scmarket.service.MarketRetentionPolicy;
import com.scmarket.service.RepositoryMarketDataIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullOpportunityPipelineIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void mockApiToImmutableProviderSnapshot() {
        PerformanceMetrics metrics = new PerformanceMetrics();
        QueueTransport transport = new QueueTransport(List.of(
                fixture("/fixtures/pipeline-listing.json"),
                fixture("/fixtures/pipeline-transactions.json")
        ));
        ApiClientConfig apiConfig = new ApiClientConfig(
                Duration.ofSeconds(1), Duration.ofSeconds(2), "pipeline-test/1", 1,
                1_048_576, new ApiRetryPolicy(1, List.of(), Duration.ZERO, 0.0D)
        );
        try (DonutApiClient api = new DonutApiClient(
                () -> Optional.of("fixture-key".toCharArray()), apiConfig, transport,
                new ApiRequestScheduler()
        )) {
            ApiAuctionPage listingPage = api.fetchRecentlyListedAuctions(1).join();
            ApiTransactionPage transactionPage = api.fetchCompletedTransactions(1).join();
            assertEquals(2, api.connectionSnapshot().successfulRequestCount());

            DatabaseManager database = new DatabaseManager(temporaryDirectory.resolve("pipeline.db"));
            RepositoryMarketDataIngestionService ingestion = new RepositoryMarketDataIngestionService(
                    database, (changed, now) -> CompletableFuture.completedFuture(changed.size()),
                    () -> CompletableFuture.completedFuture(null), MarketRetentionPolicy.defaults(), metrics
            );
            try {
                ingestion.ingestListings("pipeline-listing", listingPage, NOW).join();
                ingestion.ingestTransactions("pipeline-sales", transactionPage, NOW).join();

                NormalizedItem item = new ItemNormalizer().normalize(new ApiItemDescriptorMapper().map(
                        listingPage.listings().getFirst().item().orElseThrow()
                ));
                String fingerprint = item.fingerprint().sha256();
                assertTrue(new FingerprintRepository(database).find(fingerprint).join().isPresent());
                assertEquals(12, new SaleRepository(database).count().join());
                ListingEntity listing = new ListingRepository(database)
                        .findByFingerprint(fingerprint, 10).join().getFirst();

                RepositoryMarketStatisticsService statistics = new RepositoryMarketStatisticsService(
                        new SaleRepository(database), new ListingRepository(database),
                        new MarketStatisticsCalculator(), Clock.fixed(NOW, ZoneOffset.UTC), metrics
                );
                ItemMarketStatistics primary = statistics.statisticsFor(
                        item, statisticsConfig(MarketLookbackPeriod.THREE_DAYS)
                ).join();
                ItemMarketStatistics recent = statistics.statisticsFor(
                        item, statisticsConfig(MarketLookbackPeriod.SIX_HOURS)
                ).join();
                ItemMarketStatistics longTerm = statistics.statisticsFor(
                        item, statisticsConfig(MarketLookbackPeriod.THIRTY_DAYS)
                ).join();
                assertEquals(12, primary.comparableSaleCount());

                OpportunityEvaluation evaluation = new OpportunityEvaluator(metrics).evaluate(
                        new OpportunityEvaluationRequest(
                                listing, item, new FairValueMarketContext(primary, recent, longTerm),
                                Optional.empty(), BigDecimal.ZERO, Duration.ZERO,
                                Optional.of(Duration.ofDays(30)), 1, false
                        ),
                        evaluationConfig(), NOW
                );
                assertTrue(evaluation.accepted());
                assertTrue(evaluation.explanation().fairValue().orElseThrow().sufficientData());
                assertTrue(evaluation.explanation().profit().orElseThrow().accepted());
                assertTrue(evaluation.explanation().confidence().orElseThrow().totalScore() > 0);

                OpportunityRepository opportunityRepository = new OpportunityRepository(database);
                opportunityRepository.upsert(new OpportunityPersistenceMapper().toEntity(evaluation)).join();
                LiveMarketSnapshotService provider = new LiveMarketSnapshotService(
                        new ListingRepository(database), new SaleRepository(database), opportunityRepository,
                        Optional.empty(), metrics, Clock.fixed(NOW.plusSeconds(5), ZoneOffset.UTC),
                        Duration.ofSeconds(15)
                );
                LiveMarketSnapshot snapshot = provider.refresh().join();

                assertEquals(1, snapshot.activeOpportunities().size());
                assertEquals(evaluation.opportunityId(), snapshot.activeOpportunities().getFirst().opportunityId());
                assertFalse(snapshot.activeOpportunities().getFirst().evaluationDetails().orElseThrow().isBlank());

                PerformanceSnapshot measured = metrics.snapshot(
                        new CacheStatistics(
                                ingestion.pageHashCacheSize(), 0, 1,
                                ingestion.estimatedPageHashCacheBytes()
                        ),
                        database.queuedOperationCount(), snapshot.activeOpportunities().size()
                );
                for (PerformanceOperation operation : PerformanceOperation.values()) {
                    assertTrue(measured.timings().get(operation).samples() > 0,
                            () -> "missing timing samples for " + operation);
                    assertTrue(measured.timings().get(operation).average().compareTo(Duration.ofSeconds(5)) < 0,
                            () -> "average timing unexpectedly slow for " + operation);
                }
                System.out.println("CHUNK12_PERFORMANCE "
                        + "apiAverageMicros=" + api.connectionSnapshot().averageLatency().toNanos() / 1_000L
                        + " dbInsertAverageMicros=" + averageMicros(measured, PerformanceOperation.DATABASE_INSERTION)
                        + " comparableQueryAverageMicros="
                        + averageMicros(measured, PerformanceOperation.COMPARABLE_SALE_QUERY)
                        + " evaluationAverageMicros="
                        + averageMicros(measured, PerformanceOperation.LISTING_EVALUATION)
                        + " guiSnapshotAverageMicros="
                        + averageMicros(measured, PerformanceOperation.GUI_SNAPSHOT_GENERATION)
                        + " cacheEstimatedBytes=" + measured.caches().estimatedBytes()
                        + " databaseQueueSize=" + measured.databaseQueueSize()
                        + " opportunityQueueSize=" + measured.opportunityQueueSize());
            } finally {
                ingestion.close();
            }
        }
    }

    private static MarketStatisticsConfig statisticsConfig(MarketLookbackPeriod lookback) {
        return new MarketStatisticsConfig(
                lookback, 8, 8, new BigDecimal("3.5"), new BigDecimal("1.5"),
                100, 100, Duration.ofHours(6), RecencyWeightPolicy.defaults()
        );
    }

    private static OpportunityEvaluationConfig evaluationConfig() {
        return new OpportunityEvaluationConfig(
                "pipeline-v1", "pipeline-config-v1", "pipeline-filter-v1",
                ItemFilterPolicy.allowAll(), SupportedItemPolicy.safeDefaults(), FairValueConfig.defaults(),
                ProfitEvaluationConfig.defaults(), ConfidenceConfig.defaults(), ManipulationRiskConfig.defaults(),
                new OpportunityThresholds(0, 8, MarketRiskLevel.MODERATE),
                Duration.ofSeconds(30), Duration.ofMinutes(5)
        );
    }

    private static long averageMicros(PerformanceSnapshot measured, PerformanceOperation operation) {
        return measured.timings().get(operation).average().toNanos() / 1_000L;
    }

    private static String fixture(String resource) {
        try (InputStream stream = FullOpportunityPipelineIntegrationTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing pipeline fixture");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read pipeline fixture", error);
        }
    }

    private static final class QueueTransport implements ApiHttpTransport {
        private final Queue<String> responses;

        private QueueTransport(List<String> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public CompletableFuture<ApiHttpResponse> sendAsync(HttpRequest request) {
            String body = responses.remove();
            return CompletableFuture.completedFuture(
                    new ApiHttpResponse(200, Map.of(), body, Duration.ofMillis(3))
            );
        }
    }
}
