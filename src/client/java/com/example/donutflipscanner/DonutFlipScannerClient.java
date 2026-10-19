package com.example.donutflipscanner;

import com.example.donutflipscanner.api.FileApiCredentialsStore;
import com.example.donutflipscanner.configuration.MarketDetectionMode;
import com.example.donutflipscanner.configuration.MarketDetectionModeStore;
import com.example.donutflipscanner.configuration.ModRuntimeSettings;
import com.example.donutflipscanner.configuration.ModRuntimeSettingsStore;
import com.example.donutflipscanner.configuration.OperatingMode;
import com.example.donutflipscanner.data.ClientUiDataSources;
import com.example.donutflipscanner.data.ClientUiDataSourceRouter;
import com.example.donutflipscanner.gui.ClickGuiScreen;
import com.example.donutflipscanner.gui.hud.OpportunityHudOverlay;
import com.example.donutflipscanner.market.scanner.MarketScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class DonutFlipScannerClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);
    private static KeyBinding openGuiKey;
    private static ClientUiDataSources dataSources;
    private static ClientUiDataSourceRouter dataSourceRouter;
    private static volatile MarketScanner marketScanner;
    private static volatile ClientProductionRuntime productionRuntime;
    private static volatile CompletableFuture<Optional<ClientProductionRuntime>> productionStartup;
    private static final AtomicBoolean clientStopping = new AtomicBoolean();
    private static final AtomicLong detectionModeGeneration = new AtomicLong();
    private static final OpportunityHudOverlay HUD_OVERLAY = new OpportunityHudOverlay();
    private static volatile String currentServerAddress = "";
    private static volatile MarketDetectionMode selectedDetectionMode = MarketDetectionMode.PACKET_SCAN;
    private static volatile ModRuntimeSettings runtimeSettings = ModRuntimeSettings.defaults();

    @Override
    public void onInitializeClient() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir()
                .resolve(ModConstants.CONFIG_DIRECTORY_NAME);
        runtimeSettings = new ModRuntimeSettingsStore(configDirectory.resolve("runtime-settings.txt")).load();
        HUD_OVERLAY.setModuleEnabled(runtimeSettings.enabled());
        dataSourceRouter = new ClientUiDataSourceRouter(ClientUiDataSources.createMock());
        dataSources = dataSourceRouter.dataSources();
        KeyBinding.Category category = KeyBinding.Category.create(
                Identifier.of(ModConstants.MOD_ID, ModConstants.KEYBIND_CATEGORY_PATH)
        );
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                ModConstants.OPEN_GUI_KEY,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(ModConstants.MOD_ID, "opportunity_alerts"),
                HUD_OVERLAY::render
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            net.minecraft.client.network.ServerInfo server = client.getCurrentServerEntry();
            updateCurrentServerAddress(server == null ? "" : server.address);
            dataSources.automationSettings().snapshot();
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen instanceof ClickGuiScreen) {
                    client.setScreen(null);
                } else if (client.currentScreen == null) {
                    client.setScreen(new ClickGuiScreen(dataSources));
                }
            }
            HUD_OVERLAY.tick(dataSources);
            ClientProductionRuntime runtime = productionRuntime;
            if (runtime != null) {
                runtime.tickFishing();
            }
        });

        FileApiCredentialsStore credentials = new FileApiCredentialsStore(configDirectory.resolve("api-key.txt"));
        selectedDetectionMode = new MarketDetectionModeStore(configDirectory.resolve("market-detection-mode.txt"))
                .load(hasApiKey(credentials) ? MarketDetectionMode.API : MarketDetectionMode.PACKET_SCAN);
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
        return runtime == null ? "Fishing runtime is not ready; choose Packet Scan or configure API access."
                : runtime.fishingStatus();
    }

    public static void setModEnabled(boolean enabled) {
        updateRuntimeSettings(new ModRuntimeSettings(enabled, runtimeSettings.mode()));
    }

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
        String username = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
        String uuid = Optional.ofNullable(net.minecraft.client.MinecraftClient.getInstance()
                .getSession().getUuidOrNull()).map(java.util.UUID::toString).orElse("");
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
