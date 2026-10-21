package com.scmarket.gui;

import com.scmarket.ModConstants;
import com.scmarket.ScMarketClient;
import com.scmarket.data.ApiConnectionStatus;
import com.scmarket.data.ClientUiDataSources;
import com.scmarket.data.MarketStatistics;
import com.scmarket.data.ScannerStatus;
import com.scmarket.gui.navigation.SidebarNavigation;
import com.scmarket.gui.panel.ShellTabPanel;
import com.scmarket.gui.panel.TabContentPanel;
import com.scmarket.gui.render.GuiDraw;
import com.scmarket.gui.theme.GuiColors;
import com.scmarket.gui.theme.GuiTypography;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ClickGuiScreen extends Screen {
    private static final long TAB_DISSOLVE_DURATION_NANOS = 180_000_000L;
    private static final int TAB_DISSOLVE_MAXIMUM_ALPHA = 216;
    private static final Identifier DONUT_LOGO = Identifier.fromNamespaceAndPath(
            ModConstants.MOD_ID, "textures/gui/sc_market_logo.png"
    );
    private static final int BRAND_LOGO_SIZE = 22;
    private static final int SCANNER_TOGGLE_WIDTH = 58;
    private static final int SCANNER_TOGGLE_HEIGHT = 18;
    private static final int SCANNER_TOGGLE_X = GuiLayout.LOGICAL_WIDTH - SCANNER_TOGGLE_WIDTH - 12;
    private static final int SCANNER_TOGGLE_Y = 10;

    private final ClientUiDataSources dataSources;
    private final SidebarNavigation navigation = new SidebarNavigation();
    private final Map<GuiTab, TabContentPanel> panels = new EnumMap<>(GuiTab.class);

    private GuiTab selectedTab = GuiTab.DASHBOARD;
    private GuiTab outgoingTab;
    private long tabTransitionStartedAt;
    private GuiLayout layout;

    public ClickGuiScreen(ClientUiDataSources dataSources) {
        super(Component.literal(ModConstants.MOD_NAME));
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        for (GuiTab tab : GuiTab.values()) {
            panels.put(tab, new ShellTabPanel(tab, dataSources));
        }
    }

    @Override
    protected void init() {
        layout = GuiLayout.fit(width, height);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        layout = GuiLayout.fit(width, height);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (layout == null) {
            layout = GuiLayout.fit(width, height);
        }

        int logicalMouseX = layout.toLogicalX(mouseX);
        int logicalMouseY = layout.toLogicalY(mouseY);
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(layout.screenX(), layout.screenY());
        matrices.scale(layout.scale(), layout.scale());

        renderFrame(context);
        renderHeader(context, logicalMouseX, logicalMouseY);
        navigation.render(context, font, selectedTab, logicalMouseX, logicalMouseY);
        renderTabContent(context, logicalMouseX, logicalMouseY, System.nanoTime());
        renderStatusBar(context);

        matrices.popMatrix();
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderFrame(GuiGraphics context) {
        GuiDraw.fillVerticalGradient(context, 0, 0,
                GuiLayout.LOGICAL_WIDTH, GuiLayout.LOGICAL_HEIGHT,
                GuiColors.FRAME_GRADIENT_TOP, GuiColors.FRAME_GRADIENT_BOTTOM);
        GuiDraw.strokeRect(context, 0, 0, GuiLayout.LOGICAL_WIDTH,
                GuiLayout.LOGICAL_HEIGHT, GuiColors.PRIMARY_BORDER);
        GuiDraw.fillVerticalGradient(context, 1, GuiLayout.HEADER_HEIGHT,
                GuiLayout.SIDEBAR_WIDTH - 2,
                GuiLayout.LOGICAL_HEIGHT - GuiLayout.HEADER_HEIGHT - GuiLayout.STATUS_BAR_HEIGHT,
                GuiColors.SIDEBAR_GRADIENT_TOP, GuiColors.SIDEBAR_GRADIENT_BOTTOM);
        GuiDraw.fillHorizontalGradient(context, 1, 1,
                GuiLayout.LOGICAL_WIDTH - 2, GuiLayout.HEADER_HEIGHT - 2,
                GuiColors.HEADER_GRADIENT_LEFT, GuiColors.HEADER_GRADIENT_RIGHT);
        context.fill(1, GuiLayout.HEADER_HEIGHT - 1, GuiLayout.LOGICAL_WIDTH - 1, GuiLayout.HEADER_HEIGHT, GuiColors.SECONDARY_BORDER);
        context.fill(GuiLayout.SIDEBAR_WIDTH - 1, GuiLayout.HEADER_HEIGHT, GuiLayout.SIDEBAR_WIDTH, GuiLayout.LOGICAL_HEIGHT - GuiLayout.STATUS_BAR_HEIGHT, GuiColors.SECONDARY_BORDER);
        context.fill(1, GuiLayout.LOGICAL_HEIGHT - GuiLayout.STATUS_BAR_HEIGHT - 1, GuiLayout.LOGICAL_WIDTH - 1, GuiLayout.LOGICAL_HEIGHT - GuiLayout.STATUS_BAR_HEIGHT, GuiColors.SECONDARY_BORDER);
    }

    private void renderHeader(GuiGraphics context, int mouseX, int mouseY) {
        context.blit(
                RenderPipelines.GUI_TEXTURED,
                DONUT_LOGO,
                12,
                8,
                0.0F,
                0.0F,
                BRAND_LOGO_SIZE,
                BRAND_LOGO_SIZE,
                500,
                500,
                500,
                500
        );
        renderScannerToggle(context, ScMarketClient.runtimeSettings().enabled(),
                isInsideScannerToggle(mouseX, mouseY));
    }

    private void renderScannerToggle(GuiGraphics context, boolean enabled, boolean hovered) {
        int trackColor = enabled
                ? hovered ? GuiColors.TOGGLE_ON_HOVER : GuiColors.TOGGLE_ON_TRACK
                : hovered ? GuiColors.TOGGLE_OFF_HOVER : GuiColors.TOGGLE_OFF_TRACK;
        GuiDraw.fillRoundedRect(
                context,
                SCANNER_TOGGLE_X,
                SCANNER_TOGGLE_Y,
                SCANNER_TOGGLE_WIDTH,
                SCANNER_TOGGLE_HEIGHT,
                SCANNER_TOGGLE_HEIGHT / 2,
                trackColor
        );
        String label = "Mod";
        GuiTypography.drawCentered(context, font, label,
                SCANNER_TOGGLE_X + SCANNER_TOGGLE_WIDTH / 2,
                SCANNER_TOGGLE_Y + 5,
                enabled ? GuiColors.INVERSE_TEXT : GuiColors.PRIMARY_TEXT);
    }

    private void renderStatusBar(GuiGraphics context) {
        ApiConnectionStatus api = dataSources.apiConnectionStatus().getApiConnectionStatus();
        MarketStatistics statistics = dataSources.marketStatistics().getMarketStatistics();
        int y = GuiLayout.LOGICAL_HEIGHT - 14;
        int x = 10;

        x = drawStatusText(context, "API: ", GuiColors.PRIMARY_TEXT, x, y);
        x = drawStatusText(context, api.displayName(), GuiColors.SECONDARY_TEXT, x, y);
        x = drawStatusText(context, "  |  Last Update: ", GuiColors.MUTED_TEXT, x, y);
        x = drawStatusText(context, api.lastUpdateText(), GuiColors.SECONDARY_TEXT, x, y);
        x = drawStatusText(context, "  |  Opportunities: ", GuiColors.MUTED_TEXT, x, y);
        drawStatusText(context, Integer.toString(statistics.opportunitiesFound()), GuiColors.PRIMARY_TEXT, x, y);
    }

    private void renderTabContent(GuiGraphics context, int mouseX, int mouseY, long now) {
        if (outgoingTab == null) {
            panels.get(selectedTab).render(context, font, mouseX, mouseY);
            return;
        }

        long elapsed = Math.max(0L, now - tabTransitionStartedAt);
        if (elapsed >= TAB_DISSOLVE_DURATION_NANOS) {
            outgoingTab = null;
            panels.get(selectedTab).render(context, font, mouseX, mouseY);
            return;
        }

        float progress = elapsed / (float) TAB_DISSOLVE_DURATION_NANOS;
        GuiTab renderedTab = progress < 0.5F ? outgoingTab : selectedTab;
        panels.get(renderedTab).render(context, font, mouseX, mouseY);

        float dissolve = 1.0F - Math.abs(progress * 2.0F - 1.0F);
        float eased = dissolve * dissolve * (3.0F - 2.0F * dissolve);
        int alpha = Math.round(TAB_DISSOLVE_MAXIMUM_ALPHA * eased);
        int overlay = alpha << 24 | GuiColors.TAB_DISSOLVE_RGB;
        context.fill(
                GuiLayout.SIDEBAR_WIDTH,
                GuiLayout.HEADER_HEIGHT,
                GuiLayout.LOGICAL_WIDTH - 1,
                GuiLayout.LOGICAL_HEIGHT - GuiLayout.STATUS_BAR_HEIGHT,
                overlay
        );
    }

    private int drawStatusText(GuiGraphics context, String text, int color, int x, int y) {
        GuiTypography.draw(context, font, text, x, y, color, false);
        return x + GuiTypography.width(font, text);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout != null) {
            int logicalX = layout.toLogicalX(click.x());
            int logicalY = layout.toLogicalY(click.y());
            if (isInsideScannerToggle(logicalX, logicalY)) {
                ScMarketClient.setModEnabled(!ScMarketClient.runtimeSettings().enabled());
                return true;
            }
            GuiTab clickedTab = navigation.findTabAt(
                    logicalX,
                    logicalY
            );
            if (clickedTab != null) {
                if (clickedTab != selectedTab) {
                    outgoingTab = selectedTab;
                    selectedTab = clickedTab;
                    tabTransitionStartedAt = System.nanoTime();
                }
                return true;
            }
            return panels.get(selectedTab).mouseClicked(logicalX, logicalY, click.button());
        }
        return true;
    }

    private boolean isInsideScannerToggle(int x, int y) {
        return x >= SCANNER_TOGGLE_X
                && x < SCANNER_TOGGLE_X + SCANNER_TOGGLE_WIDTH
                && y >= SCANNER_TOGGLE_Y
                && y < SCANNER_TOGGLE_Y + SCANNER_TOGGLE_HEIGHT;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (layout == null) {
            return true;
        }
        return panels.get(selectedTab).mouseScrolled(
                layout.toLogicalX(mouseX),
                layout.toLogicalY(mouseY),
                verticalAmount
        );
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (panels.get(selectedTab).keyPressed(input)) {
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return panels.get(selectedTab).charTyped(input) || super.charTyped(input);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
