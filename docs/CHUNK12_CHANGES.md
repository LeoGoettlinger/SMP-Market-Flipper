# Chunk 12 file manifest

## New production source

```text
src/main/java/com/example/donutflipscanner/api/FileApiCredentialsStore.java
src/main/java/com/example/donutflipscanner/configuration/AppConfig.java
src/main/java/com/example/donutflipscanner/configuration/ConfigurationCodec.java
src/main/java/com/example/donutflipscanner/configuration/ConfigurationException.java
src/main/java/com/example/donutflipscanner/configuration/ConfigurationLoadResult.java
src/main/java/com/example/donutflipscanner/configuration/ConfigurationManager.java
src/main/java/com/example/donutflipscanner/configuration/DebouncedConfigurationStore.java
src/main/java/com/example/donutflipscanner/configuration/FilterConfigurationIO.java
src/main/java/com/example/donutflipscanner/configuration/ItemThresholdConfig.java
src/main/java/com/example/donutflipscanner/database/DatabaseRecoveryException.java
src/main/java/com/example/donutflipscanner/database/DatabaseRecoveryManager.java
src/main/java/com/example/donutflipscanner/diagnostics/CacheStatistics.java
src/main/java/com/example/donutflipscanner/diagnostics/DatabaseRecordCounts.java
src/main/java/com/example/donutflipscanner/diagnostics/DiagnosticsExporter.java
src/main/java/com/example/donutflipscanner/diagnostics/DiagnosticsService.java
src/main/java/com/example/donutflipscanner/diagnostics/DiagnosticsSnapshot.java
src/main/java/com/example/donutflipscanner/diagnostics/DiagnosticsVersions.java
src/main/java/com/example/donutflipscanner/diagnostics/PerformanceMetrics.java
src/main/java/com/example/donutflipscanner/diagnostics/PerformanceOperation.java
src/main/java/com/example/donutflipscanner/diagnostics/PerformanceSnapshot.java
src/main/java/com/example/donutflipscanner/diagnostics/TimingMetricSnapshot.java
src/main/java/com/example/donutflipscanner/market/opportunity/OpportunityPersistenceMapper.java
src/main/java/com/example/donutflipscanner/security/JsonSafety.java
src/main/java/com/example/donutflipscanner/security/SensitiveDataSanitizer.java
src/client/java/com/example/donutflipscanner/gui/hud/OpportunityAlertQueue.java
src/client/java/com/example/donutflipscanner/gui/hud/OpportunityHudOverlay.java
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
src/main/java/com/example/donutflipscanner/api/ApiClientConfig.java
src/main/java/com/example/donutflipscanner/api/ApiConnectionSnapshot.java
src/main/java/com/example/donutflipscanner/api/ApiConnectionStateTracker.java
src/main/java/com/example/donutflipscanner/api/ApiResponseValidator.java
src/main/java/com/example/donutflipscanner/api/DonutApiClient.java
src/main/java/com/example/donutflipscanner/api/JdkApiHttpTransport.java
src/main/java/com/example/donutflipscanner/database/DatabaseException.java
src/main/java/com/example/donutflipscanner/database/DatabaseManager.java
src/main/java/com/example/donutflipscanner/database/ListingRepository.java
src/main/java/com/example/donutflipscanner/database/MarketStatisticsRepository.java
src/main/java/com/example/donutflipscanner/market/opportunity/OpportunityDetector.java
src/main/java/com/example/donutflipscanner/market/opportunity/OpportunityEvaluator.java
src/main/java/com/example/donutflipscanner/market/scanner/ApiMarketScanWork.java
src/main/java/com/example/donutflipscanner/market/statistics/RepositoryMarketStatisticsService.java
src/main/java/com/example/donutflipscanner/provider/LiveMarketSnapshotService.java
src/main/java/com/example/donutflipscanner/service/RepositoryMarketDataIngestionService.java
src/client/java/com/example/donutflipscanner/DonutFlipScannerClient.java
src/client/java/com/example/donutflipscanner/data/provider/DatabaseOpportunityHistoryProvider.java
src/client/java/com/example/donutflipscanner/data/provider/MarketOpportunityProvider.java
```

## New and changed tests/fixtures

```text
src/test/java/com/example/donutflipscanner/api/FileApiCredentialsStoreTest.java
src/test/java/com/example/donutflipscanner/client/OpportunityAlertQueueTest.java
src/test/java/com/example/donutflipscanner/configuration/ConfigurationManagerTest.java
src/test/java/com/example/donutflipscanner/database/DatabaseCorruptionRecoveryTest.java
src/test/java/com/example/donutflipscanner/diagnostics/DiagnosticsExporterTest.java
src/test/java/com/example/donutflipscanner/integration/FullOpportunityPipelineIntegrationTest.java
src/test/java/com/example/donutflipscanner/market/opportunity/OpportunityDetectorTest.java
src/test/java/com/example/donutflipscanner/security/SecurityHardeningTest.java
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
