package com.scmarket.gui.panel;

import com.scmarket.command.AuctionSearchCommand;
import com.scmarket.ScMarketClient;
import com.scmarket.configuration.MarketDetectionMode;
import com.scmarket.configuration.MarketModule;
import com.scmarket.configuration.OrderModuleSettings;
import com.scmarket.data.ApiConnectionStatus;
import com.scmarket.data.ClientUiDataSources;
import com.scmarket.data.FlipOpportunity;
import com.scmarket.data.ItemFilterSnapshot;
import com.scmarket.data.MarketStatistics;
import com.scmarket.data.MarketChartPoint;
import com.scmarket.data.MarketChartSnapshot;
import com.scmarket.data.OpportunityHistoryEntry;
import com.scmarket.data.ScannerStatus;
import com.scmarket.data.AutomationSettingsSnapshot;
import com.scmarket.automation.model.AutomationMode;
import com.scmarket.gui.GuiLayout;
import com.scmarket.gui.GuiTab;
import com.scmarket.gui.render.GuiDraw;
import com.scmarket.gui.theme.GuiColors;
import com.scmarket.gui.theme.GuiTypography;
import com.scmarket.market.opportunity.ItemFilterMode;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/** Provider-driven panels for the spacious blue trading workspace. */
public final class ShellTabPanel implements TabContentPanel {
    private static final int CONTENT_X = GuiLayout.SIDEBAR_WIDTH + 12;
    private static final int CONTENT_RIGHT = GuiLayout.LOGICAL_WIDTH - 12;
    private static final int CONTENT_WIDTH = CONTENT_RIGHT - CONTENT_X;
    private static final int CONTENT_Y = GuiLayout.HEADER_HEIGHT + 10;
    private static final int CARD_TOP = CONTENT_Y + 30;

    private static final int OPPORTUNITY_GRID_TOP = CARD_TOP + 25;
    private static final int OPPORTUNITY_GAP = 6;
    private static final int OPPORTUNITY_CARD_WIDTH = (CONTENT_WIDTH - OPPORTUNITY_GAP) / 2;
    private static final int OPPORTUNITY_CARD_HEIGHT = 49;
    private static final int OPPORTUNITY_ROW_GAP = 5;
    private static final int MAX_VISIBLE_OPPORTUNITIES = 8;
    private static final int SETTINGS_PANEL_WIDTH = (CONTENT_WIDTH - 7) / 2;
    private static final int NOTIFICATION_TOGGLE_WIDTH = 42;
    private static final int NOTIFICATION_TOGGLE_HEIGHT = 15;
    private static final int PURCHASE_MODAL_WIDTH = 300;
    private static final int PURCHASE_MODAL_HEIGHT = 82;

    private final GuiTab tab;
    private final ClientUiDataSources dataSources;
    private volatile String notice = "";
    private int opportunityOffset;
    private EditBox apiKeyField;
    private EditBox tradeItemField;
    private int whitelistOffset;
    private String pendingPurchaseId;
    private String pendingPurchaseLabel;

    private static final int AUTOMATION_LEFT_WIDTH = 225;
    private static final int AUTOMATION_RIGHT_X = CONTENT_X + AUTOMATION_LEFT_WIDTH + 7;

    public ShellTabPanel(GuiTab tab, ClientUiDataSources dataSources) {
        this.tab = Objects.requireNonNull(tab, "tab");
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
    }

    @Override
    public void render(GuiGraphicsExtractor context, Font textRenderer, int mouseX, int mouseY) {
        GuiTypography.draw(context, textRenderer, tab.displayName(), CONTENT_X, CONTENT_Y,
                GuiColors.PRIMARY_TEXT, false);
        GuiTypography.draw(context, textRenderer, fit(textRenderer, tab.description(), CONTENT_WIDTH),
                CONTENT_X, CONTENT_Y + 13, GuiColors.SECONDARY_TEXT, false);
        switch (tab) {
            case DASHBOARD -> renderDashboard(context, textRenderer);
            case FISHING -> renderFishing(context, textRenderer, mouseX, mouseY);
            case OPPORTUNITIES -> renderOpportunities(context, textRenderer, mouseX, mouseY);
            case ITEM_FILTERS -> renderFilters(context, textRenderer, mouseX, mouseY);
            case HISTORY -> renderHistory(context, textRenderer);
            case AUTOMATION -> renderAutomation(context, textRenderer, mouseX, mouseY);
            case SETTINGS -> renderSettings(context, textRenderer, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (tab == GuiTab.OPPORTUNITIES) {
            return clickOpportunity(mouseX, mouseY);
        }
        if (tab == GuiTab.FISHING) {
            return clickFishing(mouseX, mouseY);
        }
        if (tab == GuiTab.AUTOMATION) {
            return clickAutomation(mouseX, mouseY);
        }
        if (tab == GuiTab.ITEM_FILTERS) {
            ItemFilterMode[] modes = ItemFilterMode.values();
            for (int index = 0; index < modes.length; index++) {
                int y = CARD_TOP + 39 + index * 37;
                if (contains(mouseX, mouseY, CONTENT_X + 9, y, 202, 29)) {
                    dataSources.itemFilters().setMode(modes[index])
                            .whenComplete((ignored, error) -> notice = error == null
                                    ? "Filter mode updated; reevaluation queued."
                                    : "Unable to update filter mode.");
                    return true;
                }
            }
        }
        if (tab == GuiTab.ITEM_FILTERS && clickOrderSettings(mouseX, mouseY)) {
            return true;
        }
        if (tab == GuiTab.SETTINGS) {
            MarketModule[] modules = MarketModule.values();
            for (int index = 0; index < modules.length; index++) {
                int y = CARD_TOP + 73 + index * 22;
                if (contains(mouseX, mouseY, CONTENT_X + 10, y, SETTINGS_PANEL_WIDTH - 20, 18)) {
                    MarketModule module = modules[index];
                    boolean enabled = !ScMarketClient.runtimeSettings().moduleEnabled(module);
                    ScMarketClient.setModuleEnabled(module, enabled);
                    notice = module.name() + (enabled ? " selected" : " paused")
                            + (module == MarketModule.ORDER_TO_AH && enabled
                                    ? "; no Order/AH action runs until its private GUI profile is verified."
                                    : "; GUI actions remain serialized and authorization gates still apply.");
                    return true;
                }
            }
        }
        if (tab == GuiTab.SETTINGS && contains(mouseX, mouseY,
                CONTENT_X + 10, CARD_TOP + 50, SETTINGS_PANEL_WIDTH - 20, 18)) {
            MarketDetectionMode next = ScMarketClient.selectedDetectionMode() == MarketDetectionMode.API
                    ? MarketDetectionMode.PACKET_SCAN : MarketDetectionMode.API;
            try {
                ScMarketClient.selectDetectionMode(next);
                notice = detectionModeLabel(next) + " selected; the scanner is switching now.";
            } catch (RuntimeException failure) {
                notice = Objects.requireNonNullElse(failure.getMessage(), "Unable to save detection mode preference.");
            }
            return true;
        }
        if (tab == GuiTab.SETTINGS && apiKeyField != null && contains(mouseX, mouseY,
                apiKeyFieldX(), apiKeyFieldY(), apiKeyFieldWidth(), 16)) {
            apiKeyField.onClick(new MouseButtonEvent(mouseX, mouseY,
                    new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
            return true;
        }
        if (tab == GuiTab.SETTINGS && contains(mouseX, mouseY,
                apiKeySaveX(), apiKeyFieldY(), 30, 16)) {
            char[] key = apiKeyField == null ? new char[0] : apiKeyField.getValue().toCharArray();
            try {
                ScMarketClient.saveApiKey(key);
                notice = "API key saved securely; API mode reconnects now when selected.";
                if (apiKeyField != null) {
                    apiKeyField.setValue("");
                    apiKeyField.setFocused(false);
                }
            } catch (RuntimeException failure) {
                notice = "API key could not be saved; check that it is valid and the config folder is writable.";
            } finally {
                java.util.Arrays.fill(key, '\0');
            }
            return true;
        }
        if (tab == GuiTab.SETTINGS && contains(mouseX, mouseY,
                apiKeyClearX(), apiKeyFieldY(), 34, 16)) {
            try {
                boolean apiModeWasSelected = ScMarketClient.selectedDetectionMode() == MarketDetectionMode.API;
                ScMarketClient.clearApiKey();
                if (apiKeyField != null) {
                    apiKeyField.setValue("");
                    apiKeyField.setFocused(false);
                }
                notice = apiModeWasSelected
                        ? "API key removed; switched to Packet Scan, which needs no API key."
                        : "Stored API key removed; Packet Scan remains available without a key.";
            } catch (RuntimeException failure) {
                notice = "Unable to remove the stored API key.";
            }
            return true;
        }
        if (tab == GuiTab.SETTINGS && contains(
                mouseX, mouseY, notificationToggleX(), notificationToggleY(),
                NOTIFICATION_TOGGLE_WIDTH, NOTIFICATION_TOGGLE_HEIGHT
        )) {
            dataSources.notificationSettings().toggleNotifications();
            boolean enabled = dataSources.notificationSettings().getNotificationSettings().enabled();
            notice = enabled ? "HUD notifications enabled." : "HUD notifications disabled.";
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, double verticalAmount) {
        if (tab == GuiTab.ITEM_FILTERS
                && contains(mouseX, mouseY, CONTENT_X + 237, CARD_TOP + 145,
                CONTENT_RIGHT - CONTENT_X - 247, 36)) {
            int size = ScMarketClient.orderModuleSettings().itemWhitelist().size();
            int maximumOffset = Math.max(0, size - 2);
            int direction = verticalAmount < 0.0D ? 1 : verticalAmount > 0.0D ? -1 : 0;
            whitelistOffset = Math.max(0, Math.min(maximumOffset, whitelistOffset + direction));
            return true;
        }
        if (tab != GuiTab.OPPORTUNITIES
                || !contains(mouseX, mouseY, CONTENT_X, OPPORTUNITY_GRID_TOP,
                CONTENT_WIDTH, OPPORTUNITY_CARD_HEIGHT * 4 + OPPORTUNITY_ROW_GAP * 3)) {
            return false;
        }
        List<FlipOpportunity> opportunities = dataSources.opportunities().getOpportunities();
        int maximumOffset = Math.max(0, opportunities.size() - MAX_VISIBLE_OPPORTUNITIES);
        int direction = verticalAmount < 0.0D ? 2 : verticalAmount > 0.0D ? -2 : 0;
        opportunityOffset = Math.max(0, Math.min(maximumOffset, opportunityOffset + direction));
        return true;
    }

    private boolean clickOpportunity(int mouseX, int mouseY) {
        if (pendingPurchaseId != null) {
            int modalX = CONTENT_X + (CONTENT_WIDTH - PURCHASE_MODAL_WIDTH) / 2;
            int modalY = CARD_TOP + 65;
            if (contains(mouseX, mouseY, modalX + 154, modalY + 58, 62, 15)) {
                pendingPurchaseId = null;
                pendingPurchaseLabel = null;
                notice = "Purchase tracking cancelled.";
                return true;
            }
            if (contains(mouseX, mouseY, modalX + 222, modalY + 58, 69, 15)) {
                String opportunityId = pendingPurchaseId;
                pendingPurchaseId = null;
                pendingPurchaseLabel = null;
                notice = "Recording confirmed purchase...";
                dataSources.opportunities().markPurchasedManually(opportunityId)
                        .whenComplete((updated, error) -> notice = error == null && updated
                                ? "Purchase tracked; waiting for your completed sale."
                                : "Listing was no longer verified; purchase not tracked.");
                return true;
            }
            return true;
        }
        List<FlipOpportunity> opportunities = dataSources.opportunities().getOpportunities();
        clampOpportunityOffset(opportunities.size());
        int visible = Math.min(MAX_VISIBLE_OPPORTUNITIES, opportunities.size() - opportunityOffset);
        for (int visibleIndex = 0; visibleIndex < visible; visibleIndex++) {
            int x = opportunityCardX(visibleIndex);
            int y = opportunityCardY(visibleIndex);
            FlipOpportunity opportunity = opportunities.get(opportunityOffset + visibleIndex);
            if (contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 153, y + 31, 44, 13)) {
                pendingPurchaseId = opportunity.opportunityId();
                pendingPurchaseLabel = opportunity.itemName() + " x" + opportunity.count()
                        + " for $" + compact(opportunity.listingPrice());
                return true;
            }
            if (contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 105, y + 31, 24, 13)) {
                notice = "Submitting guarded execution...";
                dataSources.automationSettings().executeConfiguredMode(opportunity)
                        .whenComplete((result, error) -> notice = error == null
                                ? result.message() : "Unable to update opportunity state.");
                return true;
            }
            if (contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 77, y + 31, 20, 13)) {
                String command = AuctionSearchCommand.forOpportunity(opportunity);
                Minecraft.getInstance().keyboardHandler.setClipboard(command);
                notice = "Copied " + command + " - paste it in chat.";
                return true;
            }
            if (contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 53, y + 31, 30, 13)) {
                String command = AuctionSearchCommand.sellAtTargetPrice(opportunity);
                Minecraft.getInstance().keyboardHandler.setClipboard(command);
                notice = "Copied target-price " + command + " - verify the held stack before pasting.";
                return true;
            }
            if (contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 19, y + 31, 13, 13)) {
                notice = "Dismissing...";
                dataSources.opportunities().dismiss(opportunity.opportunityId())
                        .whenComplete((updated, error) -> notice = error == null && updated
                                ? "Opportunity dismissed." : "Unable to dismiss opportunity.");
                return true;
            }
        }
        return false;
    }

    private void renderDashboard(GuiGraphicsExtractor context, Font textRenderer) {
        ScannerStatus scanner = dataSources.scannerStatus().getScannerStatus();
        ApiConnectionStatus api = dataSources.apiConnectionStatus().getApiConnectionStatus();
        MarketStatistics market = dataSources.marketStatistics().getMarketStatistics();
        int gap = 7;
        int cardWidth = (CONTENT_WIDTH - gap * 2) / 3;
        metric(context, textRenderer, CONTENT_X, CARD_TOP, cardWidth, "SCANNER", scanner.lifecycleState());
        metric(context, textRenderer, CONTENT_X + cardWidth + gap, CARD_TOP, cardWidth,
                "DATA SOURCE", api.displayName());
        metric(context, textRenderer, CONTENT_X + (cardWidth + gap) * 2, CARD_TOP, cardWidth,
                "ACTIVE LISTINGS", compact(market.activeListings()));
        metric(context, textRenderer, CONTENT_X, CARD_TOP + 57, cardWidth,
                "STORED SALES", compact(market.storedTransactions()));
        metric(context, textRenderer, CONTENT_X + cardWidth + gap, CARD_TOP + 57, cardWidth,
                "OPPORTUNITIES", Integer.toString(market.opportunitiesFound()));
        metric(context, textRenderer, CONTENT_X + (cardWidth + gap) * 2, CARD_TOP + 57, cardWidth,
                "POTENTIAL PROFIT", "$" + compact(market.combinedPotentialProfit()));

        renderMarketChart(context, textRenderer, dataSources.marketChart().getMarketChart(), CARD_TOP + 121);
    }

    private void renderFishing(GuiGraphicsExtractor context, Font renderer, int mouseX, int mouseY) {
        AutomationSettingsSnapshot snapshot = dataSources.automationSettings().snapshot();
        int right = CONTENT_X + 227;
        int rightWidth = CONTENT_RIGHT - right;
        panel(context, CONTENT_X, CARD_TOP, 220, 211);
        panel(context, right, CARD_TOP, rightWidth, 211);

        boolean moduleOn = ScMarketClient.runtimeSettings().moduleEnabled(MarketModule.FISHING);
        boolean fishingArmed = moduleOn && snapshot.configuration().enabled()
                && snapshot.configuration().mode() == AutomationMode.AUTOMATIC_AUTHORIZED_SERVER;
        String address = ScMarketClient.currentServerAddress().strip();
        boolean connected = !address.isBlank();
        boolean allowed = connected && snapshot.configuration().serverAllowed(address);
        boolean hasProfile = snapshot.configuration().interactionProfile().isPresent();
        boolean packetMode = ScMarketClient.selectedDetectionMode() == MarketDetectionMode.PACKET_SCAN;
        Minecraft client = Minecraft.getInstance();
        boolean rodReady = client.player != null && hasFishingRodInHotbar(client.player);
        boolean inventoryReady = client.player != null && hasFreeInventorySlot(client.player);

        GuiTypography.draw(context, renderer, "FISHING CONTROLS", CONTENT_X + 10, CARD_TOP + 11,
                GuiColors.ACCENT_TEXT, false);
        button(context, renderer, CONTENT_X + 10, CARD_TOP + 27, 200, 18,
                fishingArmed ? "STOP FISHING" : "START FISHING · PRIVATE SERVER",
                contains(mouseX, mouseY, CONTENT_X + 10, CARD_TOP + 27, 200, 18));
        lineAt(context, renderer, CONTENT_X + 10, CONTENT_X + 210,
                "Server", connected ? fit(renderer, address, 120) : "Not connected", CARD_TOP + 55);
        lineAt(context, renderer, CONTENT_X + 10, CONTENT_X + 210,
                "Allowlist", allowed ? "EXACT SERVER ALLOWED" : "NOT ALLOWED", CARD_TOP + 76);
        lineAt(context, renderer, CONTENT_X + 10, CONTENT_X + 210,
                "Private AH profile", hasProfile ? "CONFIGURED" : "NOT CONFIGURED", CARD_TOP + 97);
        lineAt(context, renderer, CONTENT_X + 10, CONTENT_X + 210,
                "Rod / inventory", (rodReady ? "ROD OK" : "ADD ROD") + " · "
                        + (inventoryReady ? "SPACE OK" : "NO SPACE"), CARD_TOP + 118);
        lineAt(context, renderer, CONTENT_X + 10, CONTENT_X + 210,
                "Market data", packetMode ? "PACKET SCAN · NO API" : "API MODE", CARD_TOP + 139);
        button(context, renderer, CONTENT_X + 10, CARD_TOP + 158, 200, 18,
                allowed ? "CURRENT SERVER ALREADY ALLOWED" : "ALLOW CURRENT SERVER",
                contains(mouseX, mouseY, CONTENT_X + 10, CARD_TOP + 158, 200, 18));
        GuiTypography.draw(context, renderer, "No API key is needed for Packet Scan.",
                CONTENT_X + 10, CARD_TOP + 190, GuiColors.POSITIVE_TEXT, false);

        GuiTypography.draw(context, renderer, "LIVE FISHING STATUS", right + 10, CARD_TOP + 11,
                GuiColors.ACCENT_TEXT, false);
        drawWrapped(context, renderer, ScMarketClient.fishingStatus(), right + 10, CARD_TOP + 31,
                rightWidth - 20, GuiColors.PRIMARY_TEXT, 4);
        context.fill(right + 10, CARD_TOP + 87, CONTENT_RIGHT - 10, CARD_TOP + 88,
                GuiColors.SECONDARY_BORDER);
        GuiTypography.draw(context, renderer, "AUTOMATION CHECKLIST", right + 10, CARD_TOP + 96,
                GuiColors.ACCENT_TEXT, false);
        lineAt(context, renderer, right + 10, CONTENT_RIGHT - 10,
                "Automation", snapshot.configuration().enabled() ? "ENABLED" : "DISABLED",
                CARD_TOP + 117);
        lineAt(context, renderer, right + 10, CONTENT_RIGHT - 10,
                "Execution mode", shortMode(snapshot.configuration().mode()), CARD_TOP + 137);
        String guidance = !hasProfile
                ? "Catching can run, but listing stays off until a verified private AH profile is configured."
                : "Keep the matching AH page open for fresh Packet Scan prices. Uncertain catches stay in inventory.";
        drawWrapped(context, renderer, guidance, right + 10, CARD_TOP + 158,
                rightWidth - 20, GuiColors.MUTED_TEXT, 3);
    }

    private boolean clickFishing(int mouseX, int mouseY) {
        if (contains(mouseX, mouseY, CONTENT_X + 10, CARD_TOP + 27, 200, 18)) {
            AutomationSettingsSnapshot snapshot = dataSources.automationSettings().snapshot();
            boolean armed = ScMarketClient.runtimeSettings().moduleEnabled(MarketModule.FISHING)
                    && snapshot.configuration().enabled()
                    && snapshot.configuration().mode() == AutomationMode.AUTOMATIC_AUTHORIZED_SERVER;
            if (armed) {
                notice = "Stopping Fishing and disabling automation…";
                ScMarketClient.stopFishingOnly().whenComplete((ignored, error) -> notice = error == null
                        ? "Fishing stopped; automation is disabled."
                        : "Fishing stopped, but settings could not be saved: "
                                + Objects.requireNonNullElse(error.getMessage(), "unknown error"));
            } else {
                notice = "Starting Fishing-only mode. The exact server allowlist is still required.";
                ScMarketClient.startFishingOnly().whenComplete((ignored, error) -> notice = error == null
                        ? "Fishing-only mode is ready; it will remain paused unless this exact server is allowlisted."
                        : "Fishing setup could not finish: " + Objects.requireNonNullElse(error.getMessage(), "runtime unavailable"));
            }
            return true;
        }
        if (contains(mouseX, mouseY, CONTENT_X + 10, CARD_TOP + 158, 200, 18)) {
            if (ScMarketClient.currentServerAddress().isBlank()) {
                notice = "Connect to the authorized private server before adding it to the allowlist.";
            } else {
                dataSources.automationSettings().allowCurrentServer()
                        .whenComplete((ignored, error) -> notice = error == null
                                ? "Exact current server added to the allowlist."
                                : "Unable to allow the current server; check the Automation status.");
            }
            return true;
        }
        return false;
    }

    private static boolean hasFishingRodInHotbar(net.minecraft.client.player.LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(slot).getItem())
                    .toString().equals("minecraft:fishing_rod")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFreeInventorySlot(net.minecraft.client.player.LocalPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void renderMarketChart(
            GuiGraphicsExtractor context,
            Font renderer,
            MarketChartSnapshot chart,
            int y
    ) {
        panel(context, CONTENT_X, y, CONTENT_WIDTH, 90);
        GuiTypography.draw(context, renderer, chart.title(), CONTENT_X + 10, y + 8,
                GuiColors.ACCENT_TEXT, false);
        String currentValue = "$" + compact(chart.currentValue());
        GuiTypography.draw(context, renderer, currentValue, CONTENT_X + 10, y + 21,
                GuiColors.PRIMARY_TEXT, false);
        String change = String.format(Locale.ROOT, "%+.1f%%", chart.changePercent());
        GuiTypography.draw(context, renderer, change,
                CONTENT_X + 16 + GuiTypography.width(renderer, currentValue), y + 21,
                chart.changePercent() >= 0.0D ? GuiColors.POSITIVE_TEXT : GuiColors.NEGATIVE_TEXT, false);
        GuiTypography.draw(context, renderer, chart.rangeLabel(),
                CONTENT_RIGHT - 10 - GuiTypography.width(renderer, chart.rangeLabel()), y + 8,
                GuiColors.MUTED_TEXT, false);

        int graphX = CONTENT_X + 10;
        int graphY = y + 37;
        int graphWidth = CONTENT_WIDTH - 20;
        int graphHeight = 36;
        for (int index = 0; index < 3; index++) {
            int gridY = graphY + index * (graphHeight - 1) / 2;
            context.fill(graphX, gridY, graphX + graphWidth, gridY + 1, GuiColors.CHART_GRID);
        }

        List<MarketChartPoint> points = chart.points();
        if (points.size() < 2) {
            String emptyMessage = chart.title().startsWith("TRACKED REALIZED PROFIT")
                    ? "Confirm BOUGHT on a trade; profit appears after its completed sale."
                    : "No chart data available";
            GuiTypography.drawCentered(context, renderer, emptyMessage,
                    CONTENT_X + CONTENT_WIDTH / 2, graphY + 13, GuiColors.MUTED_TEXT);
            return;
        }
        long minimum = points.stream().mapToLong(MarketChartPoint::value).min().orElse(0L);
        long maximum = points.stream().mapToLong(MarketChartPoint::value).max().orElse(minimum + 1L);
        long spread = Math.max(1L, maximum - minimum);
        int previousX = graphX;
        int previousY = chartY(points.getFirst().value(), minimum, spread, graphY, graphHeight);
        for (int index = 1; index < points.size(); index++) {
            int pointX = graphX + Math.round(index * (graphWidth - 1) / (float) (points.size() - 1));
            int pointY = chartY(points.get(index).value(), minimum, spread, graphY, graphHeight);
            drawChartSegment(context, previousX, previousY, pointX, pointY,
                    graphY + graphHeight - 1);
            previousX = pointX;
            previousY = pointY;
        }
        context.fill(previousX - 1, previousY - 1, previousX + 2, previousY + 2,
                chart.changePercent() >= 0.0D ? GuiColors.POSITIVE_TEXT : GuiColors.NEGATIVE_TEXT);
        GuiTypography.draw(context, renderer, points.getFirst().label(), graphX, y + 77,
                GuiColors.MUTED_TEXT, false);
        String lastLabel = points.getLast().label();
        GuiTypography.draw(context, renderer, lastLabel,
                graphX + graphWidth - GuiTypography.width(renderer, lastLabel), y + 77,
                GuiColors.MUTED_TEXT, false);
    }

    private static int chartY(long value, long minimum, long spread, int graphY, int graphHeight) {
        double normalized = (value - minimum) / (double) spread;
        return graphY + graphHeight - 2 - (int) Math.round(normalized * (graphHeight - 4));
    }

    private static void drawChartSegment(
            GuiGraphicsExtractor context,
            int startX,
            int startY,
            int endX,
            int endY,
            int baselineY
    ) {
        int width = Math.max(1, endX - startX);
        for (int step = 0; step <= width; step++) {
            float progress = step / (float) width;
            int x = startX + step;
            int y = Math.round(startY + (endY - startY) * progress);
            context.fill(x, y + 1, x + 1, baselineY, GuiColors.CHART_AREA);
            context.fill(x, y, x + 1, y + 2, GuiColors.CHART_LINE);
        }
    }

    private void renderOpportunities(
            GuiGraphicsExtractor context,
            Font textRenderer,
            int mouseX,
            int mouseY
    ) {
        List<FlipOpportunity> opportunities = dataSources.opportunities().getOpportunities();
        clampOpportunityOffset(opportunities.size());
        context.fill(CONTENT_X, CARD_TOP, CONTENT_RIGHT, CARD_TOP + 20, GuiColors.PANEL_BACKGROUND);
        context.fill(CONTENT_X, CARD_TOP + 19, CONTENT_RIGHT, CARD_TOP + 20, GuiColors.SECONDARY_BORDER);

        String range = opportunityRange(opportunities.size());
        GuiTypography.draw(context, textRenderer, range, CONTENT_X + 7, CARD_TOP + 6,
                GuiColors.ACCENT_TEXT, false);
        String helper = notice.isBlank() ? "Scroll to browse more trades" : notice;
        String fittedHelper = fit(textRenderer, helper, CONTENT_WIDTH - 120);
        GuiTypography.draw(context, textRenderer, fittedHelper,
                CONTENT_RIGHT - 7 - GuiTypography.width(textRenderer, fittedHelper), CARD_TOP + 6,
                notice.isBlank() ? GuiColors.MUTED_TEXT : GuiColors.SECONDARY_TEXT, false);

        if (opportunities.isEmpty()) {
            panel(context, CONTENT_X, OPPORTUNITY_GRID_TOP, CONTENT_WIDTH, 100);
            GuiTypography.drawCentered(context, textRenderer,
                    "No live opportunities match current filters.",
                    CONTENT_X + CONTENT_WIDTH / 2, OPPORTUNITY_GRID_TOP + 44,
                    GuiColors.MUTED_TEXT);
            return;
        }

        int visible = Math.min(MAX_VISIBLE_OPPORTUNITIES, opportunities.size() - opportunityOffset);
        for (int visibleIndex = 0; visibleIndex < visible; visibleIndex++) {
            FlipOpportunity opportunity = opportunities.get(opportunityOffset + visibleIndex);
            int x = opportunityCardX(visibleIndex);
            int y = opportunityCardY(visibleIndex);
            boolean hovered = contains(mouseX, mouseY, x, y,
                    OPPORTUNITY_CARD_WIDTH, OPPORTUNITY_CARD_HEIGHT);
            opportunityCard(context, textRenderer, opportunity, x, y, hovered, mouseX, mouseY);
        }
        if (pendingPurchaseId != null) {
            renderPurchaseConfirmation(context, textRenderer, mouseX, mouseY);
        }
    }

    private void opportunityCard(
            GuiGraphicsExtractor context,
            Font renderer,
            FlipOpportunity value,
            int x,
            int y,
            boolean hovered,
            int mouseX,
            int mouseY
    ) {
        GuiDraw.fillVerticalGradient(context, x + 1, y + 1,
                OPPORTUNITY_CARD_WIDTH - 2, OPPORTUNITY_CARD_HEIGHT - 2,
                hovered ? GuiColors.HOVER_BACKGROUND : GuiColors.RAISED_PANEL,
                GuiColors.RAISED_PANEL_BOTTOM);
        GuiDraw.strokeRect(context, x, y, OPPORTUNITY_CARD_WIDTH, OPPORTUNITY_CARD_HEIGHT,
                hovered ? GuiColors.PRIMARY_BORDER : GuiColors.SUBTLE_BORDER);

        String name = fit(renderer, value.itemName() + " x" + value.count(), 121);
        GuiTypography.draw(context, renderer, name, x + 7, y + 6,
                GuiColors.PRIMARY_TEXT, false);
        String profit = "+$" + compact(value.estimatedProfit());
        GuiTypography.draw(context, renderer, profit,
                x + OPPORTUNITY_CARD_WIDTH - 7 - GuiTypography.width(renderer, profit),
                y + 6, GuiColors.POSITIVE_TEXT, false);

        String prices = "BUY $" + compact(value.listingPrice())
                + "  VALUE $" + compact(value.fairValue());
        GuiTypography.draw(context, renderer, fit(renderer, prices, OPPORTUNITY_CARD_WIDTH - 14),
                x + 7, y + 19, GuiColors.SECONDARY_TEXT, false);

        String evidence = formatPercent(value.roiPercent()) + " ROI  "
                + formatPercent(value.confidencePercent()) + " CONF  " + value.listingAge();
        GuiTypography.draw(context, renderer, fit(renderer, evidence, OPPORTUNITY_CARD_WIDTH - 160),
                x + 7, y + 34, GuiColors.MUTED_TEXT, false);
        button(context, renderer, x + OPPORTUNITY_CARD_WIDTH - 153, y + 31, 44, 13, "BOUGHT",
                contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 153, y + 31, 44, 13));
        button(context, renderer, x + OPPORTUNITY_CARD_WIDTH - 105, y + 31, 24, 13, "RUN",
                contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 105, y + 31, 24, 13));
        button(context, renderer, x + OPPORTUNITY_CARD_WIDTH - 77, y + 31, 20, 13, "AH",
                contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 77, y + 31, 20, 13));
        button(context, renderer, x + OPPORTUNITY_CARD_WIDTH - 53, y + 31, 30, 13, "SELL",
                contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 53, y + 31, 30, 13));
        button(context, renderer, x + OPPORTUNITY_CARD_WIDTH - 19, y + 31, 13, 13, "X",
                contains(mouseX, mouseY, x + OPPORTUNITY_CARD_WIDTH - 19, y + 31, 13, 13));
    }

    private void renderPurchaseConfirmation(
            GuiGraphicsExtractor context,
            Font renderer,
            int mouseX,
            int mouseY
    ) {
        int x = CONTENT_X + (CONTENT_WIDTH - PURCHASE_MODAL_WIDTH) / 2;
        int y = CARD_TOP + 65;
        panel(context, x, y, PURCHASE_MODAL_WIDTH, PURCHASE_MODAL_HEIGHT);
        GuiDraw.strokeRect(context, x, y, PURCHASE_MODAL_WIDTH, PURCHASE_MODAL_HEIGHT,
                GuiColors.PRIMARY_BORDER);
        GuiTypography.draw(context, renderer, "CONFIRM PURCHASE", x + 9, y + 9,
                GuiColors.ACCENT_TEXT, false);
        GuiTypography.draw(context, renderer,
                fit(renderer, Objects.requireNonNullElse(pendingPurchaseLabel, "Selected opportunity"),
                        PURCHASE_MODAL_WIDTH - 18),
                x + 9, y + 24, GuiColors.PRIMARY_TEXT, false);
        GuiTypography.draw(context, renderer,
                "Only confirm after you bought this exact listing.",
                x + 9, y + 39, GuiColors.MUTED_TEXT, false);
        button(context, renderer, x + 154, y + 58, 62, 15, "CANCEL",
                contains(mouseX, mouseY, x + 154, y + 58, 62, 15));
        button(context, renderer, x + 222, y + 58, 69, 15, "CONFIRM",
                contains(mouseX, mouseY, x + 222, y + 58, 69, 15));
    }

    private void renderFilters(GuiGraphicsExtractor context, Font textRenderer, int mouseX, int mouseY) {
        ItemFilterSnapshot filters = dataSources.itemFilters().getItemFilters();
        panel(context, CONTENT_X, CARD_TOP, 220, 211);
        panel(context, CONTENT_X + 227, CARD_TOP, CONTENT_WIDTH - 227, 211);

        GuiTypography.draw(context, textRenderer, "FILTER MODE", CONTENT_X + 9, CARD_TOP + 11,
                GuiColors.ACCENT_TEXT, false);
        String counts = filters.whitelistedItems().size() + " whitelisted  |  "
                + filters.blacklistedItems().size() + " blacklisted";
        GuiTypography.draw(context, textRenderer, counts, CONTENT_X + 9, CARD_TOP + 24,
                GuiColors.MUTED_TEXT, false);

        ItemFilterMode[] modes = ItemFilterMode.values();
        for (int index = 0; index < modes.length; index++) {
            int y = CARD_TOP + 39 + index * 37;
            boolean selected = modes[index] == filters.mode();
            boolean hovered = contains(mouseX, mouseY, CONTENT_X + 9, y, 202, 29);
            if (selected) {
                GuiDraw.fillHorizontalGradient(context, CONTENT_X + 9, y, 202, 29,
                        GuiColors.SELECTED_BACKGROUND, GuiColors.SELECTED_BACKGROUND_DARK);
            } else {
                context.fill(CONTENT_X + 9, y, CONTENT_X + 211, y + 29,
                        hovered ? GuiColors.HOVER_BACKGROUND : GuiColors.PANEL_BACKGROUND);
            }
            GuiTypography.draw(context, textRenderer, modeName(modes[index]), CONTENT_X + 18, y + 11,
                    selected ? GuiColors.PRIMARY_TEXT : GuiColors.SECONDARY_TEXT, false);
        }

        renderOrderModuleSettings(context, textRenderer, mouseX, mouseY, CONTENT_X + 237);
    }

    private void renderOrderModuleSettings(
            GuiGraphicsExtractor context, Font renderer, int mouseX, int mouseY, int rightX
    ) {
        OrderModuleSettings settings = ScMarketClient.orderModuleSettings();
        int rightEdge = CONTENT_RIGHT - 10;
        GuiTypography.draw(context, renderer, "ORDER → AH SETTINGS", rightX, CARD_TOP + 11,
                GuiColors.ACCENT_TEXT, false);
        drawSettingStepper(context, renderer, rightX, rightEdge, "Order size", settings.orderQuantity(),
                orderQuantityButtonY(), 70, mouseX, mouseY);
        drawSettingStepper(context, renderer, rightX, rightEdge, "Total active orders",
                settings.maximumTotalOrders(), totalOrdersButtonY(), 70, mouseX, mouseY);
        drawSettingStepper(context, renderer, rightX, rightEdge, "Orders per item",
                settings.maximumOrdersPerItem(), perItemButtonY(), 70, mouseX, mouseY);
        drawSettingStepper(context, renderer, rightX, rightEdge, "Minimum profit / item",
                "$" + settings.minimumProfitPerItem().stripTrailingZeros().toPlainString(),
                minimumProfitButtonY(), 70, mouseX, mouseY);

        context.fill(rightX, CARD_TOP + 109, rightEdge, CARD_TOP + 110, GuiColors.SECONDARY_BORDER);
        GuiTypography.draw(context, renderer,
                "TRADE ITEMS  ·  " + settings.itemWhitelist().size() + " selected",
                rightX, CARD_TOP + 114, GuiColors.SECONDARY_TEXT, false);
        button(context, renderer, rightEdge - 112, CARD_TOP + 111, 112, 15,
                settings.whitelistOnly() ? "WHITELIST ONLY · ON" : "WHITELIST ONLY · OFF",
                contains(mouseX, mouseY, rightEdge - 112, CARD_TOP + 111, 112, 15));
        if (tradeItemField == null) {
            tradeItemField = new EditBox(renderer, rightX, tradeItemFieldY(),
                    Math.max(70, rightEdge - rightX - 38), 16, Component.literal("Trade whitelist item ID"));
            tradeItemField.setMaxLength(256);
            tradeItemField.setTextShadow(false);
            tradeItemField.setHint(Component.literal("minecraft:diamond"));
        }
        tradeItemField.extractRenderState(context, mouseX, mouseY, 0.0F);
        button(context, renderer, rightEdge - 34, tradeItemFieldY(), 34, 16, "ADD",
                contains(mouseX, mouseY, rightEdge - 34, tradeItemFieldY(), 34, 16));

        var whitelist = settings.itemWhitelist().stream().sorted().toList();
        if (whitelist.isEmpty()) {
            GuiTypography.draw(context, renderer,
                    settings.whitelistOnly() ? "Empty whitelist = no trades (fail-closed)"
                            : "Whitelist-only OFF = all supported items",
                    rightX, whitelistRowY(0) + 4, GuiColors.MUTED_TEXT, false);
        } else {
            whitelistOffset = Math.max(0, Math.min(whitelistOffset, Math.max(0, whitelist.size() - 2)));
            for (int index = 0; index < Math.min(2, whitelist.size() - whitelistOffset); index++) {
                String itemId = whitelist.get(whitelistOffset + index);
                int y = whitelistRowY(index);
                button(context, renderer, rightX, y, rightEdge - rightX, 13,
                        fit(renderer, "−  " + itemId, rightEdge - rightX - 8),
                        contains(mouseX, mouseY, rightX, y, rightEdge - rightX, 13));
            }
            if (whitelist.size() > 2) {
                String page = (whitelistOffset + 1) + "–" + Math.min(whitelistOffset + 2, whitelist.size())
                        + " / " + whitelist.size() + "  ·  scroll to browse";
                GuiTypography.draw(context, renderer, page, rightX, CARD_TOP + 177,
                        GuiColors.MUTED_TEXT, false);
            }
        }

        boolean recorderActive = settings.localActionRecording()
                && ScMarketClient.actionRecorder() != null && ScMarketClient.actionRecorder().isRecording();
        String recordingLabel = !settings.localActionRecording() ? "LOCAL CAPTURE · OFF"
                : recorderActive ? "LOCAL CAPTURE · ON" : "CAPTURE STOPPED · TOGGLE OFF";
        button(context, renderer, rightX, actionRecordingButtonY(), 142, 15, recordingLabel,
                contains(mouseX, mouseY, rightX, actionRecordingButtonY(), 142, 15));
        button(context, renderer, rightEdge - 82, actionRecordingButtonY(), 82, 15,
                "CAP " + settings.maximumRecordedActions(),
                contains(mouseX, mouseY, rightEdge - 82, actionRecordingButtonY(), 82, 15));
        if (!notice.isBlank()) {
            GuiTypography.draw(context, renderer, fit(renderer, notice, rightEdge - rightX),
                    rightX, CARD_TOP + 198, GuiColors.MUTED_TEXT, false);
        }
    }

    private void drawSettingStepper(
            GuiGraphicsExtractor context, Font renderer, int left, int right, String label,
            Object value, int buttonY, int buttonWidth, int mouseX, int mouseY
    ) {
        GuiTypography.draw(context, renderer, label, left, buttonY + 5, GuiColors.MUTED_TEXT, false);
        button(context, renderer, right - buttonWidth, buttonY, buttonWidth, 16,
                String.valueOf(value), contains(mouseX, mouseY, right - buttonWidth, buttonY,
                        buttonWidth, 16));
    }

    private boolean clickOrderSettings(int mouseX, int mouseY) {
        OrderModuleSettings current = ScMarketClient.orderModuleSettings();
        int rightX = CONTENT_X + 237;
        int rightEdge = CONTENT_RIGHT - 10;
        int buttonX = rightEdge - 70;
        if (contains(mouseX, mouseY, buttonX, orderQuantityButtonY(), 70, 16)) {
            int[] choices = {256, 512, 1_024, 2_048};
            int next = nextValue(choices, current.orderQuantity());
            return updateOrderSettings(value -> new OrderModuleSettings(value.itemWhitelist(), value.whitelistOnly(), next,
                    value.maximumTotalOrders(), value.maximumOrdersPerItem(), value.minimumProfitPerItem(),
                    value.localActionRecording(), value.maximumRecordedActions()),
                    "Order size set to " + next + ".");
        }
        if (contains(mouseX, mouseY, buttonX, totalOrdersButtonY(), 70, 16)) {
            int next = current.maximumTotalOrders() >= OrderModuleSettings.MAXIMUM_TOTAL_ORDERS
                    ? 1 : current.maximumTotalOrders() + 1;
            return updateOrderSettings(value -> new OrderModuleSettings(value.itemWhitelist(), value.whitelistOnly(),
                    value.orderQuantity(), next, value.maximumOrdersPerItem(), value.minimumProfitPerItem(),
                    value.localActionRecording(), value.maximumRecordedActions()),
                    "Total order limit set to " + next + ".");
        }
        if (contains(mouseX, mouseY, buttonX, perItemButtonY(), 70, 16)) {
            int next = current.maximumOrdersPerItem() >= OrderModuleSettings.MAXIMUM_ORDERS_PER_ITEM
                    ? 1 : current.maximumOrdersPerItem() + 1;
            return updateOrderSettings(value -> new OrderModuleSettings(value.itemWhitelist(), value.whitelistOnly(),
                    value.orderQuantity(), value.maximumTotalOrders(), next, value.minimumProfitPerItem(),
                    value.localActionRecording(), value.maximumRecordedActions()),
                    "Per-item order limit set to " + next + ".");
        }
        if (contains(mouseX, mouseY, buttonX, minimumProfitButtonY(), 70, 16)) {
            BigDecimal[] choices = {BigDecimal.ONE, new BigDecimal("2"), new BigDecimal("5"),
                    new BigDecimal("10"), new BigDecimal("25"), new BigDecimal("100")};
            BigDecimal next = choices[0];
            boolean foundNext = false;
            for (int index = 0; index < choices.length; index++) {
                if (choices[index].compareTo(current.minimumProfitPerItem()) > 0) {
                    next = choices[index];
                    foundNext = true;
                    break;
                }
                if (choices[index].compareTo(current.minimumProfitPerItem()) == 0) {
                    next = choices[(index + 1) % choices.length];
                    foundNext = true;
                    break;
                }
            }
            if (!foundNext) {
                next = choices[0];
            }
            BigDecimal selectedProfit = next;
            return updateOrderSettings(value -> new OrderModuleSettings(value.itemWhitelist(), value.whitelistOnly(),
                    value.orderQuantity(), value.maximumTotalOrders(), value.maximumOrdersPerItem(), selectedProfit,
                    value.localActionRecording(), value.maximumRecordedActions()),
                    "Minimum profit set to $" + selectedProfit.toPlainString() + " per item.");
        }
        if (contains(mouseX, mouseY, rightEdge - 112, CARD_TOP + 111, 112, 15)) {
            boolean enabled = !current.whitelistOnly();
            return updateOrderSettings(value -> value.withWhitelistOnly(enabled), enabled
                    ? current.itemWhitelist().isEmpty()
                            ? "Whitelist-only mode is empty; no Order→AH trades are allowed until items are added."
                            : "Only explicitly listed items may be traded."
                    : "Whitelist filter disabled; all supported items may be considered.");
        }
        int whitelistButtonX = rightEdge - 34;
        if (contains(mouseX, mouseY, rightX, tradeItemFieldY(),
                Math.max(70, rightEdge - rightX - 38), 16)) {
            if (tradeItemField != null) {
                tradeItemField.onClick(new MouseButtonEvent(mouseX, mouseY,
                        new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
            }
            return true;
        }
        if (contains(mouseX, mouseY, whitelistButtonX, tradeItemFieldY(), 34, 16)) {
            String itemId = tradeItemField == null ? "" : tradeItemField.getValue().strip();
            if (itemId.isEmpty()) {
                notice = "Enter a namespaced item ID such as minecraft:diamond.";
                return true;
            }
            boolean updated = updateOrderSettings(value -> value.withWhitelistItem(itemId),
                    "Added " + itemId + " to the Order→AH trade whitelist.");
            if (updated && tradeItemField != null) {
                tradeItemField.setValue("");
                tradeItemField.setFocused(false);
            }
            return true;
        }
        OrderModuleSettings settings = ScMarketClient.orderModuleSettings();
        var whitelist = settings.itemWhitelist().stream().sorted().toList();
        for (int index = 0; index < Math.min(2, Math.max(0, whitelist.size() - whitelistOffset)); index++) {
            int y = whitelistRowY(index);
            if (contains(mouseX, mouseY, rightX, y, rightEdge - rightX, 13)) {
                String removed = whitelist.get(whitelistOffset + index);
                return updateOrderSettings(value -> value.withoutWhitelistItem(removed),
                        "Removed " + removed + "; click ADD to restore it.");
            }
        }
        if (contains(mouseX, mouseY, rightX, actionRecordingButtonY(), 142, 15)) {
            boolean enabled = !settings.localActionRecording();
            boolean updated = updateOrderSettings(value -> new OrderModuleSettings(value.itemWhitelist(), value.whitelistOnly(),
                    value.orderQuantity(), value.maximumTotalOrders(), value.maximumOrdersPerItem(),
                    value.minimumProfitPerItem(), enabled, value.maximumRecordedActions()),
                    enabled ? "Local GUI/action capture enabled for private testing; chat and typed text are excluded."
                            : "Local GUI/action capture disabled.");
            if (updated && enabled && ScMarketClient.actionRecorder() != null) {
                ScMarketClient.actionRecorder().activeFile().ifPresent(path ->
                        notice = "Recording locally to " + path.getFileName() + ".");
            }
            return true;
        }
        if (contains(mouseX, mouseY, rightEdge - 82, actionRecordingButtonY(), 82, 15)) {
            int[] choices = {100, 500, 1_000, 2_500, 5_000, 10_000};
            int next = nextValue(choices, settings.maximumRecordedActions());
            return updateOrderSettings(value -> new OrderModuleSettings(value.itemWhitelist(), value.whitelistOnly(),
                    value.orderQuantity(), value.maximumTotalOrders(), value.maximumOrdersPerItem(),
                    value.minimumProfitPerItem(), value.localActionRecording(), next),
                    "Local capture limit set to " + next + " GUI actions.");
        }
        return false;
    }

    private boolean updateOrderSettings(
            java.util.function.UnaryOperator<OrderModuleSettings> change, String successMessage
    ) {
        try {
            ScMarketClient.updateOrderModuleSettings(change);
            notice = successMessage;
        } catch (java.io.IOException | IllegalArgumentException failure) {
            notice = "Setting was not changed: " + failure.getMessage();
        }
        return true;
    }

    private static int nextValue(int[] choices, int current) {
        for (int index = 0; index < choices.length; index++) {
            if (choices[index] == current) {
                return choices[(index + 1) % choices.length];
            }
            if (choices[index] > current) {
                return choices[index];
            }
        }
        return choices[0];
    }

    private void renderHistory(GuiGraphicsExtractor context, Font textRenderer) {
        List<OpportunityHistoryEntry> history = dataSources.history().getHistory();
        panel(context, CONTENT_X, CARD_TOP, CONTENT_WIDTH, 211);
        context.fill(CONTENT_X + 1, CARD_TOP + 1, CONTENT_RIGHT - 1, CARD_TOP + 23,
                GuiColors.PANEL_BACKGROUND);
        tableHeader(context, textRenderer, "ITEM", CONTENT_X + 9, CARD_TOP + 8);
        tableHeader(context, textRenderer, "PROFIT", CONTENT_X + 204, CARD_TOP + 8);
        tableHeader(context, textRenderer, "ROI", CONTENT_X + 277, CARD_TOP + 8);
        tableHeader(context, textRenderer, "CONFIDENCE", CONTENT_X + 323, CARD_TOP + 8);
        tableHeader(context, textRenderer, "STATE", CONTENT_X + 385, CARD_TOP + 8);
        if (history.isEmpty()) {
            GuiTypography.drawCentered(context, textRenderer, "No stored opportunity history.",
                    CONTENT_X + CONTENT_WIDTH / 2, CARD_TOP + 102, GuiColors.MUTED_TEXT);
            return;
        }
        int visible = Math.min(9, history.size());
        for (int index = 0; index < visible; index++) {
            OpportunityHistoryEntry value = history.get(index);
            int y = CARD_TOP + 24 + index * 20;
            if ((index & 1) == 0) {
                context.fill(CONTENT_X + 1, y, CONTENT_RIGHT - 1, y + 19,
                        GuiColors.RAISED_PANEL_BOTTOM);
            }
            GuiTypography.draw(context, textRenderer, fit(textRenderer, value.itemName(), 183),
                    CONTENT_X + 9, y + 6, GuiColors.PRIMARY_TEXT, false);
            GuiTypography.draw(context, textRenderer, "$" + compact(value.estimatedProfit()),
                    CONTENT_X + 204, y + 6, GuiColors.POSITIVE_TEXT, false);
            GuiTypography.draw(context, textRenderer, formatPercent(value.roiPercent()),
                    CONTENT_X + 277, y + 6, GuiColors.SECONDARY_TEXT, false);
            GuiTypography.draw(context, textRenderer, formatPercent(value.confidencePercent()),
                    CONTENT_X + 323, y + 6, GuiColors.SECONDARY_TEXT, false);
            GuiTypography.draw(context, textRenderer, fit(textRenderer, value.state(), 52),
                    CONTENT_X + 385, y + 6, GuiColors.MUTED_TEXT, false);
        }
    }

    private void renderAutomation(
            GuiGraphicsExtractor context,
            Font renderer,
            int mouseX,
            int mouseY
    ) {
        AutomationSettingsSnapshot value = dataSources.automationSettings().snapshot();
        int left = CONTENT_X;
        int right = AUTOMATION_RIGHT_X;
        int rightWidth = CONTENT_RIGHT - right;
        panel(context, left, CARD_TOP, AUTOMATION_LEFT_WIDTH, 211);
        panel(context, right, CARD_TOP, rightWidth, 211);

        GuiTypography.draw(context, renderer, "EXECUTION SAFETY", left + 10, CARD_TOP + 11,
                GuiColors.ACCENT_TEXT, false);
        GuiTypography.draw(context, renderer, "Enabled", left + 10, CARD_TOP + 34,
                GuiColors.MUTED_TEXT, false);
        settingsToggle(context, renderer, left + AUTOMATION_LEFT_WIDTH - 52, CARD_TOP + 28,
                value.configuration().enabled(), contains(mouseX, mouseY,
                        left + AUTOMATION_LEFT_WIDTH - 52, CARD_TOP + 28,
                        NOTIFICATION_TOGGLE_WIDTH, NOTIFICATION_TOGGLE_HEIGHT));
        GuiTypography.draw(context, renderer, "Mode", left + 10, CARD_TOP + 57,
                GuiColors.MUTED_TEXT, false);
        button(context, renderer, left + 68, CARD_TOP + 51, 145, 18,
                shortMode(value.configuration().mode()),
                contains(mouseX, mouseY, left + 68, CARD_TOP + 51, 145, 18));
        GuiTypography.draw(context, renderer, "Current server", left + 10, CARD_TOP + 81,
                GuiColors.MUTED_TEXT, false);
        GuiTypography.draw(context, renderer, fit(renderer, value.currentServer(), 126),
                left + AUTOMATION_LEFT_WIDTH - 10
                        - GuiTypography.width(renderer, fit(renderer, value.currentServer(), 126)),
                CARD_TOP + 81, GuiColors.PRIMARY_TEXT, false);
        boolean serverAllowed = !value.currentServer().equals("Not connected")
                && value.configuration().allowedServerAddresses().contains(
                        value.currentServer().strip().toLowerCase(java.util.Locale.ROOT));
        lineAt(context, renderer, left + 10, left + AUTOMATION_LEFT_WIDTH - 10,
                "Allowlist", value.configuration().allowedServerAddresses().size() + " exact",
                CARD_TOP + 103);
        button(context, renderer, left + 10, CARD_TOP + 119, AUTOMATION_LEFT_WIDTH - 20, 18,
                "ALLOW CURRENT SERVER",
                contains(mouseX, mouseY, left + 10, CARD_TOP + 119,
                        AUTOMATION_LEFT_WIDTH - 20, 18));
        GuiTypography.draw(context, renderer, "Action delay", left + 10, CARD_TOP + 148,
                GuiColors.MUTED_TEXT, false);
        button(context, renderer, left + AUTOMATION_LEFT_WIDTH - 96, CARD_TOP + 143, 86, 18,
                actionDelayLabel(value.configuration().actionDelayMillis()),
                contains(mouseX, mouseY, left + AUTOMATION_LEFT_WIDTH - 96, CARD_TOP + 143, 86, 18));
        lineAt(context, renderer, left + 10, left + AUTOMATION_LEFT_WIDTH - 10,
                "API balance", balanceText(value.balance()), CARD_TOP + 170);
        GuiTypography.draw(context, renderer, fit(renderer, value.balance().message(), AUTOMATION_LEFT_WIDTH - 20),
                left + 10, CARD_TOP + 194,
                value.balance().status() == com.scmarket.balance.BalanceStatus.ERROR
                        ? GuiColors.NEGATIVE_TEXT : GuiColors.MUTED_TEXT, false);

        GuiTypography.draw(context, renderer, "EXECUTION STATUS", right + 10, CARD_TOP + 11,
                GuiColors.ACCENT_TEXT, false);
        lineAt(context, renderer, right + 10, CONTENT_RIGHT - 10,
                "Server access", serverAllowed ? "ALLOWLISTED" : "NOT ALLOWLISTED", CARD_TOP + 34);
        lineAt(context, renderer, right + 10, CONTENT_RIGHT - 10,
                "State", value.execution().activeState().map(Enum::name).orElse("IDLE"), CARD_TOP + 55);
        lineAt(context, renderer, right + 10, CONTENT_RIGHT - 10,
                "Purchases", Integer.toString(value.execution().purchasesThisSession()), CARD_TOP + 76);
        context.fill(right + 10, CARD_TOP + 91, CONTENT_RIGHT - 10, CARD_TOP + 92,
                GuiColors.SECONDARY_BORDER);
        drawWrapped(context, renderer,
                notice.isBlank() ? value.execution().statusMessage() : notice,
                right + 10, CARD_TOP + 103, rightWidth - 20,
                value.execution().emergencyStopped() ? GuiColors.NEGATIVE_TEXT : GuiColors.MUTED_TEXT, 3);

        int half = (rightWidth - 27) / 2;
        button(context, renderer, right + 10, CARD_TOP + 143, half, 18, "CONFIRM TRADE",
                contains(mouseX, mouseY, right + 10, CARD_TOP + 143, half, 18));
        button(context, renderer, right + 17 + half, CARD_TOP + 143, half, 18, "CANCEL EXECUTION",
                contains(mouseX, mouseY, right + 17 + half, CARD_TOP + 143, half, 18));
        button(context, renderer, right + 10, CARD_TOP + 168, rightWidth - 20, 18,
                value.execution().emergencyStopped() ? "CLEAR STOP" : "EMERGENCY STOP",
                contains(mouseX, mouseY, right + 10, CARD_TOP + 168, rightWidth - 20, 18));
        GuiTypography.draw(context, renderer, fit(renderer, value.warning(), rightWidth - 20),
                right + 10, CARD_TOP + 195, GuiColors.MUTED_TEXT, false);
    }

    private boolean clickAutomation(int mouseX, int mouseY) {
        AutomationSettingsSnapshot snapshot = dataSources.automationSettings().snapshot();
        if (contains(mouseX, mouseY, CONTENT_X + AUTOMATION_LEFT_WIDTH - 52, CARD_TOP + 28,
                NOTIFICATION_TOGGLE_WIDTH, NOTIFICATION_TOGGLE_HEIGHT)) {
            dataSources.automationSettings().setEnabled(!snapshot.configuration().enabled())
                    .whenComplete((ignored, error) -> notice = error == null
                            ? "Automation setting saved."
                            : "Unable to save automation setting.");
            return true;
        }
        if (contains(mouseX, mouseY, CONTENT_X + 68, CARD_TOP + 51, 145, 18)) {
            AutomationMode next = nextMode(snapshot.configuration().mode());
            dataSources.automationSettings().setMode(next)
                    .whenComplete((ignored, error) -> notice = error == null
                            ? "Mode changed to " + shortMode(next) + "."
                            : "Unable to save automation mode.");
            return true;
        }
        if (contains(mouseX, mouseY, CONTENT_X + 10, CARD_TOP + 119,
                AUTOMATION_LEFT_WIDTH - 20, 18)) {
            dataSources.automationSettings().allowCurrentServer()
                    .whenComplete((ignored, error) -> notice = error == null
                            ? "Exact current server added to allowlist."
                            : "Unable to allow current server.");
            return true;
        }
        if (contains(mouseX, mouseY, CONTENT_X + AUTOMATION_LEFT_WIDTH - 96,
                CARD_TOP + 143, 86, 18)) {
            long next = nextActionDelay(snapshot.configuration().actionDelayMillis());
            dataSources.automationSettings().setActionDelayMillis(next)
                    .whenComplete((ignored, error) -> notice = error == null
                            ? "Action pacing set to " + actionDelayLabel(next) + "."
                            : "Unable to save action pacing.");
            return true;
        }
        int rightWidth = CONTENT_RIGHT - AUTOMATION_RIGHT_X;
        int half = (rightWidth - 27) / 2;
        if (contains(mouseX, mouseY, AUTOMATION_RIGHT_X + 10, CARD_TOP + 143, half, 18)) {
            dataSources.automationSettings().confirmPending()
                    .whenComplete((result, error) -> notice = error == null
                            ? result.message() : "Unable to confirm pending execution.");
            return true;
        }
        if (contains(mouseX, mouseY, AUTOMATION_RIGHT_X + 17 + half, CARD_TOP + 143, half, 18)) {
            dataSources.automationSettings().cancelActiveExecution();
            notice = "Active or pending execution cancelled.";
            return true;
        }
        if (contains(mouseX, mouseY, AUTOMATION_RIGHT_X + 10, CARD_TOP + 168, rightWidth - 20, 18)) {
            if (snapshot.execution().emergencyStopped()) {
                dataSources.automationSettings().clearEmergencyStop();
                notice = "Emergency stop cleared; current settings and allowlist still apply.";
            } else {
                dataSources.automationSettings().emergencyStop();
                notice = "Emergency stop active; all execution is blocked.";
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (pendingPurchaseId != null && input.key() == GLFW.GLFW_KEY_ESCAPE) {
            pendingPurchaseId = null;
            pendingPurchaseLabel = null;
            notice = "Purchase tracking cancelled.";
            return true;
        }
        if (tab == GuiTab.ITEM_FILTERS && tradeItemField != null && tradeItemField.isFocused()) {
            return tradeItemField.keyPressed(input);
        }
        return tab == GuiTab.SETTINGS && apiKeyField != null && apiKeyField.isFocused()
                && apiKeyField.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (tab == GuiTab.ITEM_FILTERS && tradeItemField != null && tradeItemField.isFocused()) {
            return tradeItemField.charTyped(input);
        }
        return tab == GuiTab.SETTINGS && apiKeyField != null && apiKeyField.isFocused()
                && apiKeyField.charTyped(input);
    }

    private static AutomationMode nextMode(AutomationMode current) {
        return switch (current) {
            case DISABLED -> AutomationMode.DRY_RUN;
            case DRY_RUN -> AutomationMode.CONFIRM_EACH;
            case CONFIRM_EACH -> AutomationMode.AUTOMATIC_AUTHORIZED_SERVER;
            case AUTOMATIC_AUTHORIZED_SERVER -> AutomationMode.DISABLED;
        };
    }

    private static String shortMode(AutomationMode mode) {
        return switch (mode) {
            case DISABLED -> "DISABLED";
            case DRY_RUN -> "DRY RUN";
            case CONFIRM_EACH -> "CONFIRM EACH";
            case AUTOMATIC_AUTHORIZED_SERVER -> "ALLOWLIST AUTO";
        };
    }

    private static long nextActionDelay(long current) {
        return switch ((int) current) {
            case 0 -> 250L;
            case 250 -> 500L;
            case 500 -> 1_000L;
            case 1_000 -> 2_000L;
            case 2_000 -> 5_000L;
            case 5_000 -> 10_000L;
            default -> 0L;
        };
    }

    private static String actionDelayLabel(long millis) {
        return millis == 0L ? "OFF" : millis + " ms";
    }

    private void renderSettings(GuiGraphicsExtractor context, Font textRenderer, int mouseX, int mouseY) {
        ScannerStatus scanner = dataSources.scannerStatus().getScannerStatus();
        ApiConnectionStatus api = dataSources.apiConnectionStatus().getApiConnectionStatus();
        boolean notifications = dataSources.notificationSettings().getNotificationSettings().enabled();
        boolean apiKeyConfigured = ScMarketClient.apiKeyConfigured();
        int panelWidth = SETTINGS_PANEL_WIDTH;
        panel(context, CONTENT_X, CARD_TOP, panelWidth, 211);
        panel(context, CONTENT_X + panelWidth + 7, CARD_TOP, panelWidth, 211);

        GuiTypography.draw(context, textRenderer, "SCANNER", CONTENT_X + 10, CARD_TOP + 11,
                GuiColors.ACCENT_TEXT, false);
        lineAt(context, textRenderer, CONTENT_X + 10, CONTENT_X + panelWidth - 10,
                "State", scanner.lifecycleState(), CARD_TOP + 34);
        button(context, textRenderer, CONTENT_X + 10, CARD_TOP + 50, panelWidth - 20, 18,
                detectionModeLabel(ScMarketClient.selectedDetectionMode()),
                contains(mouseX, mouseY, CONTENT_X + 10, CARD_TOP + 50, panelWidth - 20, 18));
        MarketModule[] modules = MarketModule.values();
        for (int index = 0; index < modules.length; index++) {
            MarketModule module = modules[index];
            int y = CARD_TOP + 73 + index * 22;
            boolean active = ScMarketClient.runtimeSettings().moduleEnabled(module);
            button(context, textRenderer, CONTENT_X + 10, y, panelWidth - 20, 18,
                    moduleLabel(module) + (active ? "  ·  ON" : "  ·  OFF"),
                    contains(mouseX, mouseY, CONTENT_X + 10, y, panelWidth - 20, 18));
        }
        context.fill(CONTENT_X + 10, CARD_TOP + 139, CONTENT_X + panelWidth - 10,
                CARD_TOP + 140, GuiColors.SECONDARY_BORDER);
        GuiTypography.draw(context, textRenderer, "HUD notifications", CONTENT_X + 10, CARD_TOP + 151,
                GuiColors.MUTED_TEXT, false);
        settingsToggle(context, textRenderer, notificationToggleX(), notificationToggleY(),
                notifications, contains(mouseX, mouseY, notificationToggleX(), notificationToggleY(),
                        NOTIFICATION_TOGGLE_WIDTH, NOTIFICATION_TOGGLE_HEIGHT));
        context.fill(CONTENT_X + 10, CARD_TOP + 170, CONTENT_X + panelWidth - 10,
                CARD_TOP + 171, GuiColors.SECONDARY_BORDER);
        drawWrapped(context, textRenderer,
                notice.isBlank()
                        ? ScMarketClient.runtimeSettings().moduleEnabled(MarketModule.FISHING)
                                ? ScMarketClient.fishingStatus()
                                : ScMarketClient.runtimeSettings().moduleEnabled(MarketModule.ORDER_TO_AH)
                                        ? ScMarketClient.orderStatus()
                                        : "Sort AH by lowest stack price; sort Orders by highest money per item once per login."
                        : notice,
                CONTENT_X + 10, CARD_TOP + 179, panelWidth - 20, GuiColors.MUTED_TEXT, 3);

        int rightX = CONTENT_X + panelWidth + 17;
        int rightEdge = CONTENT_RIGHT - 10;
        boolean packetMode = ScMarketClient.selectedDetectionMode() == MarketDetectionMode.PACKET_SCAN;
        GuiTypography.draw(context, textRenderer, packetMode ? "NO-API MARKET SCAN" : "API CONNECTION",
                rightX, CARD_TOP + 11, GuiColors.ACCENT_TEXT, false);
        String warning;
        if (packetMode) {
            boolean profileConfigured = dataSources.automationSettings().snapshot()
                    .configuration().interactionProfile().isPresent();
            lineAt(context, textRenderer, rightX, rightEdge,
                    "Data source", "Packet Scan · no key", CARD_TOP + 34);
            lineAt(context, textRenderer, rightX, rightEdge,
                    "Capture state", api.lastUpdateText(), CARD_TOP + 55);
            lineAt(context, textRenderer, rightX, rightEdge,
                    "Private AH profile", profileConfigured ? "CONFIGURED" : "NEEDED", CARD_TOP + 76);
            context.fill(rightX, CARD_TOP + 94, rightEdge, CARD_TOP + 95,
                    GuiColors.SECONDARY_BORDER);
            warning = api.warning().or(() -> scanner.warning()).orElse(profileConfigured
                    ? "No API key is needed. Open the profiled auction page and keep it open while the scan settles."
                    : "No API key is needed, but packet parsing requires a private AH profile with the exact title, slots, and price/seller lore prefixes.");
        } else {
            lineAt(context, textRenderer, rightX, rightEdge,
                    "Requests / window", Integer.toString(api.requestsInCurrentWindow()), CARD_TOP + 34);
            lineAt(context, textRenderer, rightX, rightEdge,
                    "Average latency", api.averageLatencyMillis() + " ms", CARD_TOP + 55);
            lineAt(context, textRenderer, rightX, rightEdge,
                    "Last update", api.lastUpdateText(), CARD_TOP + 76);
            context.fill(rightX, CARD_TOP + 94, rightEdge, CARD_TOP + 95,
                    GuiColors.SECONDARY_BORDER);
            warning = api.warning().or(() -> scanner.warning()).orElse(
                    "Live data is read asynchronously; gameplay actions remain separately gated.");
        }
        drawWrapped(context, textRenderer, warning, rightX, CARD_TOP + 108,
                rightEdge - rightX, GuiColors.MUTED_TEXT, 4);
        GuiTypography.draw(context, textRenderer,
                packetMode ? "API key optional · Packet Scan works without it"
                        : apiKeyConfigured ? "API key stored securely" : "API key required for API mode",
                rightX, CARD_TOP + 160,
                packetMode || apiKeyConfigured ? GuiColors.POSITIVE_TEXT : GuiColors.MUTED_TEXT,
                false);
        if (apiKeyField == null) {
            apiKeyField = new EditBox(textRenderer, apiKeyFieldX(), apiKeyFieldY(),
                    apiKeyFieldWidth(), 16, Component.literal("API key"));
            apiKeyField.setMaxLength(512);
            apiKeyField.setTextShadow(false);
            apiKeyField.setHint(Component.literal("Paste API key"));
        }
        apiKeyField.extractRenderState(context, mouseX, mouseY, 0.0F);
        button(context, textRenderer, apiKeySaveX(), apiKeyFieldY(), 30, 16, "SAVE",
                contains(mouseX, mouseY, apiKeySaveX(), apiKeyFieldY(), 30, 16));
        button(context, textRenderer, apiKeyClearX(), apiKeyFieldY(), 34, 16, "CLEAR",
                contains(mouseX, mouseY, apiKeyClearX(), apiKeyFieldY(), 34, 16));
    }

    private static int orderQuantityButtonY() {
        return CARD_TOP + 27;
    }

    private static int totalOrdersButtonY() {
        return CARD_TOP + 46;
    }

    private static int perItemButtonY() {
        return CARD_TOP + 65;
    }

    private static int minimumProfitButtonY() {
        return CARD_TOP + 84;
    }

    private static int tradeItemFieldY() {
        return CARD_TOP + 130;
    }

    private static int whitelistRowY(int index) {
        return CARD_TOP + 148 + index * 14;
    }

    private static int actionRecordingButtonY() {
        return CARD_TOP + 184;
    }

    private static int apiSettingsX() {
        return CONTENT_X + SETTINGS_PANEL_WIDTH + 17;
    }

    private static int apiSettingsRight() {
        return CONTENT_RIGHT - 10;
    }

    private static int apiKeyFieldX() {
        return apiSettingsX();
    }

    private static int apiKeyFieldY() {
        return CARD_TOP + 174;
    }

    private static int apiKeyFieldWidth() {
        return Math.max(70, apiSettingsRight() - apiSettingsX() - 30 - 34 - 8);
    }

    private static int apiKeySaveX() {
        return apiKeyFieldX() + apiKeyFieldWidth() + 3;
    }

    private static int apiKeyClearX() {
        return apiKeySaveX() + 32;
    }

    private static String detectionModeLabel(MarketDetectionMode mode) {
        return mode == MarketDetectionMode.API
                ? "API MODE  ·  key configured"
                : "PACKET SCAN  ·  no API key needed";
    }

    private static String moduleLabel(MarketModule module) {
        return switch (module) {
            case FISHING -> "FISHING";
            case AH_DEALS -> "AH DEALS";
            case ORDER_TO_AH -> "ORDER → AH";
        };
    }

    private static int notificationToggleX() {
        return CONTENT_X + SETTINGS_PANEL_WIDTH - 10 - NOTIFICATION_TOGGLE_WIDTH;
    }

    private static int notificationToggleY() {
        return CARD_TOP + 149;
    }

    private static void settingsToggle(
            GuiGraphicsExtractor context,
            Font renderer,
            int x,
            int y,
            boolean enabled,
            boolean hovered
    ) {
        int fill = enabled
                ? hovered ? GuiColors.TOGGLE_ON_HOVER : GuiColors.TOGGLE_ON_TRACK
                : hovered ? GuiColors.TOGGLE_OFF_HOVER : GuiColors.TOGGLE_OFF_TRACK;
        context.fill(x, y, x + NOTIFICATION_TOGGLE_WIDTH, y + NOTIFICATION_TOGGLE_HEIGHT, fill);
        GuiDraw.strokeRect(context, x, y, NOTIFICATION_TOGGLE_WIDTH, NOTIFICATION_TOGGLE_HEIGHT,
                enabled ? GuiColors.POSITIVE_TEXT : GuiColors.PRIMARY_BORDER);
        GuiTypography.drawCentered(context, renderer, enabled ? "ON" : "OFF",
                x + NOTIFICATION_TOGGLE_WIDTH / 2, y + 3,
                enabled ? GuiColors.INVERSE_TEXT : GuiColors.PRIMARY_TEXT);
    }

    private String opportunityRange(int total) {
        if (total == 0) {
            return "0 LIVE TRADES";
        }
        int first = opportunityOffset + 1;
        int last = Math.min(total, opportunityOffset + MAX_VISIBLE_OPPORTUNITIES);
        return total <= MAX_VISIBLE_OPPORTUNITIES
                ? total + " LIVE TRADES"
                : "LIVE TRADES " + first + "-" + last + " / " + total;
    }

    private void clampOpportunityOffset(int size) {
        opportunityOffset = Math.max(0,
                Math.min(Math.max(0, size - MAX_VISIBLE_OPPORTUNITIES), opportunityOffset));
    }

    private static int opportunityCardX(int visibleIndex) {
        return CONTENT_X + (visibleIndex % 2) * (OPPORTUNITY_CARD_WIDTH + OPPORTUNITY_GAP);
    }

    private static int opportunityCardY(int visibleIndex) {
        return OPPORTUNITY_GRID_TOP
                + (visibleIndex / 2) * (OPPORTUNITY_CARD_HEIGHT + OPPORTUNITY_ROW_GAP);
    }

    private static void metric(
            GuiGraphicsExtractor context,
            Font renderer,
            int x,
            int y,
            int width,
            String label,
            String value
    ) {
        panel(context, x, y, width, 50);
        GuiTypography.draw(context, renderer, label, x + 9, y + 9,
                GuiColors.MUTED_TEXT, false);
        GuiTypography.draw(context, renderer, fit(renderer, value, width - 18), x + 9, y + 27,
                GuiColors.PRIMARY_TEXT, false);
    }

    private static void lineAt(
            GuiGraphicsExtractor context,
            Font renderer,
            int left,
            int right,
            String label,
            String value,
            int y
    ) {
        GuiTypography.draw(context, renderer, label, left, y, GuiColors.MUTED_TEXT, false);
        String fitted = fit(renderer, value, Math.max(40, (right - left) / 2));
        GuiTypography.draw(context, renderer, fitted,
                right - GuiTypography.width(renderer, fitted), y,
                GuiColors.PRIMARY_TEXT, false);
    }

    private static void panel(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        GuiDraw.fillVerticalGradient(context, x + 1, y + 1, width - 2, height - 2,
                GuiColors.RAISED_PANEL, GuiColors.RAISED_PANEL_BOTTOM);
        GuiDraw.strokeRect(context, x, y, width, height, GuiColors.SECONDARY_BORDER);
    }

    private static void button(
            GuiGraphicsExtractor context,
            Font renderer,
            int x,
            int y,
            int width,
            int height,
            String label,
            boolean hovered
    ) {
        context.fill(x, y, x + width, y + height,
                hovered ? GuiColors.SELECTED_BACKGROUND : GuiColors.HOVER_BACKGROUND);
        GuiTypography.drawCentered(context, renderer, label, x + width / 2, y + 3,
                GuiColors.PRIMARY_TEXT);
    }

    private static void tableHeader(GuiGraphicsExtractor context, Font renderer, String text, int x, int y) {
        GuiTypography.draw(context, renderer, text, x, y, GuiColors.MUTED_TEXT, false);
    }

    private static void drawWrapped(
            GuiGraphicsExtractor context,
            Font renderer,
            String text,
            int x,
            int y,
            int maximumWidth,
            int color,
            int maximumLines
    ) {
        String remaining = text;
        int line = 0;
        while (!remaining.isBlank() && line < maximumLines) {
            int end = remaining.length();
            while (end > 1 && GuiTypography.width(renderer, remaining.substring(0, end)) > maximumWidth) {
                end--;
            }
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end - 1);
                if (space > 0) {
                    end = space;
                }
            }
            String rendered = remaining.substring(0, end).strip();
            GuiTypography.draw(context, renderer, rendered, x, y + line * 13, color, false);
            remaining = remaining.substring(end).strip();
            line++;
        }
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String fit(Font renderer, String text, int maximumWidth) {
        if (GuiTypography.width(renderer, text) <= maximumWidth) {
            return text;
        }
        String suffix = "...";
        int end = text.length();
        while (end > 0 && GuiTypography.width(renderer, text.substring(0, end) + suffix) > maximumWidth) {
            end--;
        }
        return text.substring(0, end) + suffix;
    }

    private static String compact(long value) {
        long absolute = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        String prefix = value < 0 ? "-" : "";
        if (absolute >= 1_000_000_000L) {
            return prefix + decimal(absolute / 1_000_000_000.0D) + "B";
        }
        if (absolute >= 1_000_000L) {
            return prefix + decimal(absolute / 1_000_000.0D) + "M";
        }
        if (absolute >= 1_000L) {
            return prefix + decimal(absolute / 1_000.0D) + "K";
        }
        return Long.toString(value);
    }

    private static String balanceText(com.scmarket.balance.BalanceSnapshot snapshot) {
        return snapshot.amount().map(value -> "$" + compact(value)).orElseGet(() -> switch (snapshot.status()) {
            case REFRESHING -> "REFRESHING";
            case ERROR -> "ERROR";
            case AVAILABLE -> "AVAILABLE";
            case UNAVAILABLE -> "UNAVAILABLE";
        });
    }

    private static String compact(BigDecimal value) {
        BigDecimal absolute = value.abs();
        String prefix = value.signum() < 0 ? "-" : "";
        BigDecimal billion = BigDecimal.valueOf(1_000_000_000L);
        BigDecimal million = BigDecimal.valueOf(1_000_000L);
        BigDecimal thousand = BigDecimal.valueOf(1_000L);
        if (absolute.compareTo(billion) >= 0) {
            return prefix + decimal(absolute.divide(billion).doubleValue()) + "B";
        }
        if (absolute.compareTo(million) >= 0) {
            return prefix + decimal(absolute.divide(million).doubleValue()) + "M";
        }
        if (absolute.compareTo(thousand) >= 0) {
            return prefix + decimal(absolute.divide(thousand).doubleValue()) + "K";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String decimal(double value) {
        return value >= 100.0D
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value).replace(".0", "");
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value);
    }

    private static String modeName(ItemFilterMode mode) {
        return switch (mode) {
            case ALL_ITEMS -> "All items";
            case WHITELIST_ONLY -> "Whitelist only";
            case ALL_EXCEPT_BLACKLIST -> "All except blacklist";
        };
    }
}
