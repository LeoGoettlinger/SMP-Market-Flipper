package com.scmarket;

import com.mojang.blaze3d.platform.InputConstants;
import com.scmarket.api.FileApiCredentialsStore;
import com.scmarket.configuration.ConfigDirectoryMigrator;
import com.scmarket.configuration.MarketDetectionMode;
import com.scmarket.configuration.MarketDetectionModeStore;
import com.scmarket.configuration.ModRuntimeSettings;
import com.scmarket.configuration.ModRuntimeSettingsStore;
import com.scmarket.configuration.OrderModuleSettings;
import com.scmarket.configuration.OrderModuleSettingsStore;
import com.scmarket.configuration.OperatingMode;
import com.scmarket.diagnostics.LocalActionRecorder;
import com.scmarket.data.ClientUiDataSources;
import com.scmarket.data.ClientUiDataSourceRouter;
import com.scmarket.gui.ClickGuiScreen;
import com.scmarket.gui.hud.OpportunityHudOverlay;
import com.scmarket.market.scanner.MarketScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ScMarketClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);
    private static KeyMapping openGuiKey;
    private static ClientUiDataSources dataSources;
    private static ClientUiDataSourceRouter dataSourceRouter;
    private static volatile MarketScanner marketScanner;
    private static volatile ClientProductionRuntime productionRuntime;
    private static volatile CompletableFuture<Optional<ClientProductionRuntime>> productionStartup;
    private static final AtomicBoolean clientStopping = new AtomicBoolean();
    private static final AtomicBoolean smokeTestStopRequested = new AtomicBoolean();
    private static final AtomicInteger smokeTestTicks = new AtomicInteger();
    private static final AtomicLong detectionModeGeneration = new AtomicLong();
    private static final OpportunityHudOverlay HUD_OVERLAY = new OpportunityHudOverlay();
    private static volatile String currentServerAddress = "";
    private static volatile MarketDetectionMode selectedDetectionMode = MarketDetectionMode.PACKET_SCAN;
    private static volatile ModRuntimeSettings runtimeSettings = ModRuntimeSettings.defaults();
    private static volatile OrderModuleSettingsStore orderModuleSettingsStore;
    private static volatile OrderModuleSettings orderModuleSettings = OrderModuleSettings.defaults();
    private static volatile LocalActionRecorder actionRecorder;

    @Override
    public void onInitializeClient() {
        Path configRoot = FabricLoader.getInstance().getConfigDir();
        try {
            ConfigDirectoryMigrator.migrate(configRoot,
                    ModConstants.LEGACY_CONFIG_DIRECTORY_NAME, ModConstants.CONFIG_DIRECTORY_NAME);
        } catch (java.io.IOException migrationFailure) {
            LOGGER.warn("Legacy settings were left untouched, but could not be copied into SC-Market; "
                    + "live automation remains fail-closed until the configuration is checked.", migrationFailure);
        }
        Path configDirectory = configRoot.resolve(ModConstants.CONFIG_DIRECTORY_NAME);
        runtimeSettings = new ModRuntimeSettingsStore(configDirectory.resolve("runtime-settings.txt")).load();
        orderModuleSettingsStore = new OrderModuleSettingsStore(configDirectory.resolve("order-module.properties"));
        try {
            orderModuleSettings = orderModuleSettingsStore.load();
        } catch (java.io.IOException invalidSettings) {
            LOGGER.warn("Order-to-AH settings could not be read; safe defaults are active.", invalidSettings);
            orderModuleSettings = OrderModuleSettings.defaults();
        }
        actionRecorder = new LocalActionRecorder(configDirectory);
        if (orderModuleSettings.localActionRecording()) {
            try {
                actionRecorder.start(orderModuleSettings.maximumRecordedActions());
            } catch (IOException recorderFailure) {
                LOGGER.warn("Opt-in local action recording could not start; no actions will be recorded.",
                        recorderFailure);
            }
        }
        HUD_OVERLAY.setModuleEnabled(runtimeSettings.enabled());
        dataSourceRouter = new ClientUiDataSourceRouter(ClientUiDataSources.createMock());
        dataSources = dataSourceRouter.dataSources();
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ModConstants.MOD_ID, ModConstants.KEYBIND_CATEGORY_PATH)
        );
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                ModConstants.OPEN_GUI_KEY,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(ModConstants.MOD_ID, "opportunity_alerts"),
                HUD_OVERLAY::extractRenderState
        );
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                return; // Slot-aware recorder below captures these clicks with item metadata.
            }
            ScreenMouseEvents.beforeMouseClick(screen).register((clickedScreen, event) -> {
                LocalActionRecorder recorder = actionRecorder;
                if (recorder == null || !recorder.isRecording()) {
                    return;
                }
                int containerId = client.player == null ? -1 : client.player.containerMenu.containerId;
                recorder.recordGuiClick(clickedScreen.getTitle().getString(), containerId, -1, "", 0,
                        event.button(), (int) Math.round(event.x()), (int) Math.round(event.y()));
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            net.minecraft.client.multiplayer.ServerData server = client.getCurrentServer();
            updateCurrentServerAddress(server == null ? "" : server.ip);
            dataSources.automationSettings().snapshot();
            while (openGuiKey.consumeClick()) {
                if (client.gui.screen() instanceof ClickGuiScreen) {
                    client.gui.setScreen(null);
                } else if (client.gui.screen() == null) {
                    client.gui.setScreen(new ClickGuiScreen(dataSources));
                }
            }
            HUD_OVERLAY.tick(dataSources);
            ClientProductionRuntime runtime = productionRuntime;
            if (runtime != null) {
                runtime.tickFishing();
            }
            if (Boolean.parseBoolean(System.getenv("SCMARKET_CI_SMOKE_TEST"))) {
                int smokeTick = smokeTestTicks.incrementAndGet();
                if (smokeTick == 1) {
                    // Login normally loads this class. Force its transformation in the offline smoke
                    // test so invalid interaction-recorder injections fail CI before release.
                    try {
                        Class.forName("net.minecraft.client.multiplayer.MultiPlayerGameMode", false,
                                ScMarketClient.class.getClassLoader());
                    } catch (ClassNotFoundException missingGameMode) {
                        throw new IllegalStateException("Minecraft client game-mode class was not found.",
                                missingGameMode);
                    } catch (Throwable transformationFailure) {
                        LOGGER.error("26.2 smoke test could not transform the game-mode mixin target.",
                                transformationFailure);
                        throw new IllegalStateException("Game-mode mixin transformation failed.",
                                transformationFailure);
                    }
                    LOGGER.info("26.2 client smoke test loaded the game-mode mixin target.");
                    client.gui.setScreen(new ClickGuiScreen(dataSources));
                    LOGGER.info("26.2 client smoke test opened the SC-Market GUI.");
                } else if (smokeTick >= 100 && smokeTestStopRequested.compareAndSet(false, true)) {
                    LOGGER.info("26.2 client smoke test rendered the SC-Market GUI for 100 ticks; shutting down.");
                    client.stop();
                }
            }
        });

        FileApiCredentialsStore credentials = new FileApiCredentialsStore(configDirectory.resolve("api-key.txt"));
        MarketDetectionModeStore detectionModeStore =
                new MarketDetectionModeStore(configDirectory.resolve("market-detection-mode.txt"));
        MarketDetectionMode storedDetectionMode = detectionModeStore.load(
                hasApiKey(credentials) ? MarketDetectionMode.API : MarketDetectionMode.PACKET_SCAN);
        selectedDetectionMode = storedDetectionMode.availableWhen(hasApiKey(credentials));
        if (selectedDetectionMode != storedDetectionMode) {
            try {
                detectionModeStore.save(selectedDetectionMode);
            } catch (RuntimeException preferenceFailure) {
                LOGGER.warn("Could not persist Packet Scan fallback; it will still run for this session.",
                        preferenceFailure);
            }
            LOGGER.info("No API key is configured; starting Packet Scan instead of API mode.");
        }
        long startupGeneration = detectionModeGeneration.get();
        productionStartup = startDetectionMode(configDirectory, selectedDetectionMode);
        productionStartup.whenComplete((runtime, error) -> {
            if (startupGeneration != detectionModeGeneration.get() || clientStopping.get()) {
                if (runtime != null) {
                    runtime.ifPresent(ClientProductionRuntime::closeAsync);
                }
                return;
            }
            if (error != null) {
                LOGGER.warn("Live market startup failed; mock data remains active: {}", sanitized(error));
                return;
            }
            if (runtime.isEmpty()) {
                LOGGER.info("{} mode selected but unavailable; check the API key or private-server profile.",
                        selectedDetectionMode);
                return;
            }
            ClientProductionRuntime started = runtime.orElseThrow();
            productionRuntime = started;
            started.applyModRuntimeSettings(runtimeSettings);
            LOGGER.info("Live market scanning started in {} detection mode.", selectedDetectionMode);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            clientStopping.set(true);
            LocalActionRecorder recorder = actionRecorder;
            if (recorder != null) {
                recorder.close();
            }
            ClientProductionRuntime runtime = productionRuntime;
            if (runtime != null) {
                runtime.close();
            } else {
                MarketScanner scanner = marketScanner;
                if (scanner != null) {
                    scanner.shutdownAsync();
                }
            }
        });
    }

    /** Compatibility hook for integrations that compose an alternate live scanner. */
    public static void attachMarketScanner(MarketScanner scanner) {
        marketScanner = scanner;
    }

    public static void useLiveDataSources(ClientUiDataSources liveSources, MarketScanner scanner) {
        Objects.requireNonNull(liveSources, "liveSources");
        Objects.requireNonNull(scanner, "scanner");
        dataSourceRouter.select(liveSources);
        marketScanner = scanner;
        scanner.updateConfiguration(scanner.configuration().withMockDataMode(false));
    }

    public static void useMockDataSources() {
        dataSourceRouter.select(ClientUiDataSources.createMock());
        MarketScanner scanner = marketScanner;
        if (scanner != null) {
            scanner.updateConfiguration(scanner.configuration().withMockDataMode(true));
        }
    }

    public static String currentServerAddress() {
        return currentServerAddress;
    }

    public static MarketDetectionMode selectedDetectionMode() {
        return selectedDetectionMode;
    }

    public static ModRuntimeSettings runtimeSettings() {
        return runtimeSettings;
    }

    public static String fishingStatus() {
        ClientProductionRuntime runtime = productionRuntime;
        return runtime == null
                ? "Fishing runtime is starting. Packet Scan needs no API key; check Settings if it stays unavailable."
                : runtime.fishingStatus();
    }

    public static String orderStatus() {
        ClientProductionRuntime runtime = productionRuntime;
        return runtime == null ? "Order→AH is safely idle until its private-server GUI profile is verified."
                : runtime.orderStatus();
    }

    public static void setModEnabled(boolean enabled) {
        updateRuntimeSettings(runtimeSettings.withEnabled(enabled));
    }

    public static void setModuleEnabled(com.scmarket.configuration.MarketModule module, boolean enabled) {
        updateRuntimeSettings(runtimeSettings.withModule(module, enabled));
    }

    public static void toggleModule(com.scmarket.configuration.MarketModule module) {
        setModuleEnabled(module, !runtimeSettings.moduleEnabled(module));
    }

    /** Starts a fishing-only authorized mode so enabling Fishing cannot also start AH buying workflows. */
    public static CompletableFuture<Void> startFishingOnly() {
        updateRuntimeSettings(new ModRuntimeSettings(true,
                Set.of(com.scmarket.configuration.MarketModule.FISHING)));
        ClientProductionRuntime runtime = productionRuntime;
        if (runtime != null) {
            return runtime.enableFishingAutomation();
        }
        CompletableFuture<Optional<ClientProductionRuntime>> startup = productionStartup;
        if (startup == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("The live market runtime has not started."));
        }
        return startup.thenCompose(optional -> optional
                .map(ClientProductionRuntime::enableFishingAutomation)
                .orElseGet(() -> CompletableFuture.failedFuture(
                        new IllegalStateException("No live market runtime is available; check Packet Scan status."))));
    }

    /** Stops the fishing-only workflow and disables global automation to avoid arming later modules. */
    public static CompletableFuture<Void> stopFishingOnly() {
        setModuleEnabled(com.scmarket.configuration.MarketModule.FISHING, false);
        ClientProductionRuntime runtime = productionRuntime;
        return runtime == null ? CompletableFuture.completedFuture(null)
                : runtime.disableFishingAutomation();
    }

    /** Compatibility hook; selecting a legacy mode intentionally makes it the only active module. */
    public static void selectOperatingMode(OperatingMode mode) {
        updateRuntimeSettings(new ModRuntimeSettings(runtimeSettings.enabled(),
                Objects.requireNonNull(mode, "mode")));
    }

    private static void updateRuntimeSettings(ModRuntimeSettings updated) {
        new ModRuntimeSettingsStore(FabricLoader.getInstance().getConfigDir()
                .resolve(ModConstants.CONFIG_DIRECTORY_NAME).resolve("runtime-settings.txt")).save(updated);
        runtimeSettings = updated;
        HUD_OVERLAY.setModuleEnabled(updated.enabled());
        ClientProductionRuntime runtime = productionRuntime;
        if (runtime != null) {
            runtime.applyModRuntimeSettings(updated);
        }
    }

    public static void selectDetectionMode(MarketDetectionMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (mode == MarketDetectionMode.API && !apiKeyConfigured()) {
            throw new IllegalStateException("API mode needs a key; Packet Scan works without one.");
        }
        Path directory = FabricLoader.getInstance().getConfigDir().resolve(ModConstants.CONFIG_DIRECTORY_NAME);
        new MarketDetectionModeStore(directory.resolve("market-detection-mode.txt")).save(mode);
        if (selectedDetectionMode != mode) {
            selectedDetectionMode = mode;
            restartDetectionSource();
        }
    }

    private static void restartDetectionSource() {
        long generation = detectionModeGeneration.incrementAndGet();
        ClientProductionRuntime previous = productionRuntime;
        productionRuntime = null;
        marketScanner = null;
        dataSourceRouter.select(ClientUiDataSources.createMock());
        if (previous != null) {
            previous.closeAsync().whenComplete((ignored, error) -> startSelectedDetectionSource(generation));
            return;
        }
        CompletableFuture<Optional<ClientProductionRuntime>> pending = productionStartup;
        if (pending != null && !pending.isDone()) {
            pending.whenComplete((created, error) -> {
                CompletableFuture<Void> close = created == null || created.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : created.orElseThrow().closeAsync();
                close.whenComplete((ignored, closeError) -> startSelectedDetectionSource(generation));
            });
            return;
        }
        startSelectedDetectionSource(generation);
    }

    private static void startSelectedDetectionSource(long generation) {
        if (generation != detectionModeGeneration.get() || clientStopping.get()) {
            return;
        }
        Path directory = FabricLoader.getInstance().getConfigDir().resolve(ModConstants.CONFIG_DIRECTORY_NAME);
        CompletableFuture<Optional<ClientProductionRuntime>> startup =
                startDetectionMode(directory, selectedDetectionMode);
        productionStartup = startup;
        startup.whenComplete((runtime, error) -> {
            if (generation != detectionModeGeneration.get() || clientStopping.get()) {
                if (runtime != null) {
                    runtime.ifPresent(ClientProductionRuntime::closeAsync);
                }
                return;
            }
            if (error != null || runtime == null || runtime.isEmpty()) {
                LOGGER.warn("Unable to switch detection source to {}: {}", selectedDetectionMode,
                        error == null ? "configuration or API key is missing" : sanitized(error));
                return;
            }
            ClientProductionRuntime started = runtime.orElseThrow();
            productionRuntime = started;
            started.applyModRuntimeSettings(runtimeSettings);
            LOGGER.info("Detection source changed live to {}.", selectedDetectionMode);
        });
    }

    public static OrderModuleSettings orderModuleSettings() {
        return orderModuleSettings;
    }

    public static LocalActionRecorder actionRecorder() {
        return actionRecorder;
    }

    public static synchronized OrderModuleSettings updateOrderModuleSettings(
            UnaryOperator<OrderModuleSettings> change
    ) throws IOException {
        OrderModuleSettings previous = orderModuleSettings;
        OrderModuleSettings updated = orderModuleSettingsStore.update(change);
        boolean recorderConfigurationChanged = previous.localActionRecording() != updated.localActionRecording()
                || previous.maximumRecordedActions() != updated.maximumRecordedActions();
        if (recorderConfigurationChanged && updated.localActionRecording()) {
            LocalActionRecorder recorder = actionRecorder;
            try {
                recorder.start(updated.maximumRecordedActions());
            } catch (IOException failure) {
                if (previous.localActionRecording()) {
                    try {
                        recorder.start(previous.maximumRecordedActions());
                    } catch (IOException restartFailure) {
                        OrderModuleSettings disabled = new OrderModuleSettings(previous.itemWhitelist(),
                                previous.whitelistOnly(), previous.orderQuantity(), previous.maximumTotalOrders(),
                                previous.maximumOrdersPerItem(), previous.minimumProfitPerItem(), false,
                                previous.maximumRecordedActions());
                        orderModuleSettingsStore.save(disabled);
                        orderModuleSettings = disabled;
                        failure.addSuppressed(restartFailure);
                        throw failure;
                    }
                } else {
                    orderModuleSettingsStore.save(previous);
                    orderModuleSettings = previous;
                    throw failure;
                }
                orderModuleSettingsStore.save(previous);
                orderModuleSettings = previous;
                throw failure;
            }
        } else if (recorderConfigurationChanged) {
            try {
                actionRecorder.stop();
            } catch (IOException stopFailure) {
                LOGGER.warn("Local action capture could not flush cleanly while being disabled.", stopFailure);
            }
        }
        orderModuleSettings = updated;
        return updated;
    }

    public static boolean apiKeyConfigured() {
        return hasApiKey(new FileApiCredentialsStore(FabricLoader.getInstance().getConfigDir()
                .resolve(ModConstants.CONFIG_DIRECTORY_NAME).resolve("api-key.txt")));
    }

    public static void saveApiKey(char[] apiKey) {
        new FileApiCredentialsStore(FabricLoader.getInstance().getConfigDir()
                .resolve(ModConstants.CONFIG_DIRECTORY_NAME).resolve("api-key.txt")).setApiKey(apiKey);
        if (selectedDetectionMode == MarketDetectionMode.API) {
            restartDetectionSource();
        }
    }

    public static void clearApiKey() {
        new FileApiCredentialsStore(FabricLoader.getInstance().getConfigDir()
                .resolve(ModConstants.CONFIG_DIRECTORY_NAME).resolve("api-key.txt")).clear();
        if (selectedDetectionMode == MarketDetectionMode.API) {
            Path directory = FabricLoader.getInstance().getConfigDir()
                    .resolve(ModConstants.CONFIG_DIRECTORY_NAME);
            selectedDetectionMode = MarketDetectionMode.PACKET_SCAN;
            try {
                new MarketDetectionModeStore(directory.resolve("market-detection-mode.txt"))
                        .save(selectedDetectionMode);
            } catch (RuntimeException preferenceFailure) {
                LOGGER.warn("API key was removed, but Packet Scan preference could not be persisted.",
                        preferenceFailure);
            }
            restartDetectionSource();
        }
    }

    private static boolean hasApiKey(FileApiCredentialsStore store) {
        char[] key = null;
        try {
            key = store.copyApiKey().orElse(null);
            return key != null;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            if (key != null) {
                java.util.Arrays.fill(key, '\0');
            }
        }
    }

    private static CompletableFuture<Optional<ClientProductionRuntime>> startDetectionMode(
            Path configDirectory, MarketDetectionMode mode
    ) {
        String username = net.minecraft.client.Minecraft.getInstance().getUser().getName();
        String uuid = Optional.ofNullable(net.minecraft.client.Minecraft.getInstance()
                .getUser().getProfileId()).map(java.util.UUID::toString).orElse("");
        return mode == MarketDetectionMode.API
                ? ClientProductionRuntime.startAsync(configDirectory, username, uuid)
                : ClientProductionRuntime.startPacketModeAsync(configDirectory, username, uuid);
    }

    static void updateCurrentServerAddress(String address) {
        currentServerAddress = Objects.requireNonNullElse(address, "");
    }

    private static String sanitized(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }
}
