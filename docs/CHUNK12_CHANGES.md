# Chunk 12 file manifest (historical implementation snapshot)

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

This manifest records files from the original Chunk 12 integration work; it is not a canonical current tree or a statement that the current uncommitted refactor has been tested. Names and paths may have moved. For current package boundaries see [Project tree](PROJECT_TREE.md), and for current verification status see [26.2 port status](PORTING_26_2.md).

## New production source

```text
src/main/java/com/scmarket/api/FileApiCredentialsStore.java
src/main/java/com/scmarket/configuration/AppConfig.java
src/main/java/com/scmarket/configuration/ConfigurationCodec.java
src/main/java/com/scmarket/configuration/ConfigurationException.java
src/main/java/com/scmarket/configuration/ConfigurationLoadResult.java
src/main/java/com/scmarket/configuration/ConfigurationManager.java
src/main/java/com/scmarket/configuration/DebouncedConfigurationStore.java
src/main/java/com/scmarket/configuration/FilterConfigurationIO.java
src/main/java/com/scmarket/configuration/ItemThresholdConfig.java
src/main/java/com/scmarket/database/DatabaseRecoveryException.java
src/main/java/com/scmarket/database/DatabaseRecoveryManager.java
src/main/java/com/scmarket/diagnostics/CacheStatistics.java
src/main/java/com/scmarket/diagnostics/DatabaseRecordCounts.java
src/main/java/com/scmarket/diagnostics/DiagnosticsExporter.java
src/main/java/com/scmarket/diagnostics/DiagnosticsService.java
src/main/java/com/scmarket/diagnostics/DiagnosticsSnapshot.java
src/main/java/com/scmarket/diagnostics/DiagnosticsVersions.java
src/main/java/com/scmarket/diagnostics/PerformanceMetrics.java
src/main/java/com/scmarket/diagnostics/PerformanceOperation.java
src/main/java/com/scmarket/diagnostics/PerformanceSnapshot.java
src/main/java/com/scmarket/diagnostics/TimingMetricSnapshot.java
src/main/java/com/scmarket/market/opportunity/OpportunityPersistenceMapper.java
src/main/java/com/scmarket/security/JsonSafety.java
src/main/java/com/scmarket/security/SensitiveDataSanitizer.java
src/client/java/com/scmarket/gui/hud/OpportunityAlertQueue.java
src/client/java/com/scmarket/gui/hud/OpportunityHudOverlay.java
```

## Changed production/build source

```text
.gitignore
README.md
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
gradle/verification-metadata.xml
src/main/java/com/scmarket/api/ApiClientConfig.java
src/main/java/com/scmarket/api/ApiConnectionSnapshot.java
src/main/java/com/scmarket/api/ApiConnectionStateTracker.java
src/main/java/com/scmarket/api/ApiResponseValidator.java
src/main/java/com/scmarket/api/DonutApiClient.java
src/main/java/com/scmarket/api/JdkApiHttpTransport.java
src/main/java/com/scmarket/database/DatabaseException.java
src/main/java/com/scmarket/database/DatabaseManager.java
src/main/java/com/scmarket/database/ListingRepository.java
src/main/java/com/scmarket/database/MarketStatisticsRepository.java
src/main/java/com/scmarket/market/opportunity/OpportunityDetector.java
src/main/java/com/scmarket/market/opportunity/OpportunityEvaluator.java
src/main/java/com/scmarket/market/scanner/ApiMarketScanWork.java
src/main/java/com/scmarket/market/statistics/RepositoryMarketStatisticsService.java
src/main/java/com/scmarket/provider/LiveMarketSnapshotService.java
src/main/java/com/scmarket/service/RepositoryMarketDataIngestionService.java
src/client/java/com/scmarket/ScMarketClient.java
src/client/java/com/scmarket/data/provider/DatabaseOpportunityHistoryProvider.java
src/client/java/com/scmarket/data/provider/MarketOpportunityProvider.java
```

## New and changed tests/fixtures

```text
src/test/java/com/scmarket/api/FileApiCredentialsStoreTest.java
src/test/java/com/scmarket/client/OpportunityAlertQueueTest.java
src/test/java/com/scmarket/configuration/ConfigurationManagerTest.java
src/test/java/com/scmarket/database/DatabaseCorruptionRecoveryTest.java
src/test/java/com/scmarket/diagnostics/DiagnosticsExporterTest.java
src/test/java/com/scmarket/integration/FullOpportunityPipelineIntegrationTest.java
src/test/java/com/scmarket/market/opportunity/OpportunityDetectorTest.java
src/test/java/com/scmarket/security/SecurityHardeningTest.java
src/test/resources/fixtures/pipeline-listing.json
src/test/resources/fixtures/pipeline-transactions.json
```

## Documentation and examples

```text
docs/CHUNK12_CHANGES.md
docs/config.example.json
docs/DATABASE.md
docs/DEPENDENCIES.md
docs/filter-export.example.json
docs/GUI_BACKEND_INTEGRATION.md
docs/PROJECT_TREE.md
docs/RELEASE_HARDENING.md
```

## Database migrations and dependency changes

No schema migration was required. Existing migrations V001 through V003 remain unchanged.
No runtime library was added. Chunk 12 adds checksum verification metadata for the complete
resolved build graph and pins the official Gradle distribution checksum.
