package com.scmarket.data;

import com.scmarket.ScMarketClient;
import com.scmarket.api.DonutApiClient;
import com.scmarket.data.provider.LiveItemFilterController;
import com.scmarket.database.DatabaseManager;
import com.scmarket.database.ListingRepository;
import com.scmarket.database.OpportunityRepository;
import com.scmarket.database.SaleRepository;
import com.scmarket.data.provider.LiveNotificationSettingsController;
import com.scmarket.data.provider.AutomationSettingsController;
import com.scmarket.data.provider.LivePersonalProfitChartProvider;
import com.scmarket.market.opportunity.ItemFilterPolicy;
import com.scmarket.market.opportunity.OpportunityDetector;
import com.scmarket.market.scanner.MarketScanner;
import com.scmarket.provider.LiveMarketSnapshotService;
import com.scmarket.profit.PersonalProfitTracker;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Consumer;

/** Composes live providers without placing backend logic in GUI classes. */
public final class LiveClientDataIntegration {
    private LiveClientDataIntegration() {
    }

    public static LiveMarketSnapshotService attach(
            DatabaseManager database,
            DonutApiClient apiClient,
            MarketScanner scanner,
            OpportunityDetector opportunityDetector,
            ItemFilterPolicy initialFilters,
            Function<ItemFilterPolicy, CompletableFuture<Void>> applyFiltersAsync
    ) {
        Objects.requireNonNull(database, "database");
        LiveMarketSnapshotService snapshots = new LiveMarketSnapshotService(
                new ListingRepository(database), new SaleRepository(database),
                new OpportunityRepository(database), Optional.ofNullable(opportunityDetector)
        );
        LiveItemFilterController filters = new LiveItemFilterController(initialFilters, applyFiltersAsync);
        ClientUiDataSources live = ClientUiDataSources.createLive(snapshots, scanner, apiClient, filters);
        ScMarketClient.useLiveDataSources(live, scanner);
        return snapshots;
    }

    public static void attach(
            LiveMarketSnapshotService snapshots,
            DonutApiClient apiClient,
            MarketScanner scanner,
            ItemFilterPolicy initialFilters,
            Function<ItemFilterPolicy, CompletableFuture<Void>> applyFiltersAsync,
            Consumer<Boolean> scannerEnabledChangeListener,
            boolean notificationsEnabled,
            boolean animationsEnabled,
            Consumer<Boolean> notificationsEnabledChangeListener,
            AutomationSettingsController automationSettings,
            PersonalProfitTracker personalProfitTracker
    ) {
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(apiClient, "apiClient");
        Objects.requireNonNull(scanner, "scanner");
        LiveItemFilterController filters = new LiveItemFilterController(initialFilters, applyFiltersAsync);
        ClientUiDataSources live = ClientUiDataSources.createLive(
                snapshots, scanner, apiClient, filters, scannerEnabledChangeListener,
                new LiveNotificationSettingsController(
                        notificationsEnabled, animationsEnabled, notificationsEnabledChangeListener
                ), automationSettings, new LivePersonalProfitChartProvider(personalProfitTracker)
        );
        ScMarketClient.useLiveDataSources(live, scanner);
    }
}
