package com.scmarket.gui.hud;

import com.scmarket.command.OpportunityClipboardTracker;
import com.scmarket.data.ClientUiDataSources;
import com.scmarket.data.FlipOpportunity;
import com.scmarket.data.NotificationSettings;
import com.scmarket.data.AutomationSettingsSnapshot;
import com.scmarket.gui.ClickGuiScreen;
import com.scmarket.gui.render.GuiDraw;
import com.scmarket.gui.theme.GuiColors;
import com.scmarket.gui.theme.GuiTypography;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Small Geist HUD alert. It reads provider snapshots and performs no I/O. */
public final class OpportunityHudOverlay {
    private static final int WIDTH = 178;
    private static final int HEIGHT = 50;
    private static final long FADE_IN_NANOS = Duration.ofMillis(180).toNanos();
    private final OpportunityAlertQueue alerts = new OpportunityAlertQueue(
            Duration.ofSeconds(6), Duration.ofSeconds(30)
    );
    private final Clock clock;
    private final OpportunityClipboardTracker clipboardTracker;
    private volatile boolean notificationsEnabled = true;
    private volatile boolean animationsEnabled = true;
    private volatile boolean moduleEnabled = true;
    private volatile ExecutionNotice executionNotice;
    private volatile String lastExecutionStatus = "";

    public OpportunityHudOverlay() {
        this(Clock.systemUTC(), command -> Minecraft.getInstance().keyboardHandler.setClipboard(command));
    }

    OpportunityHudOverlay(Clock clock) {
        this(clock, ignored -> {
        });
    }

    OpportunityHudOverlay(Clock clock, java.util.function.Consumer<String> clipboardWriter) {
        this.clock = clock;
        this.clipboardTracker = new OpportunityClipboardTracker(clipboardWriter);
    }

    public void setModuleEnabled(boolean enabled) {
        moduleEnabled = enabled;
        if (!enabled) {
            alerts.clear();
            executionNotice = null;
        }
    }

    public void tick(ClientUiDataSources dataSources) {
        if (!moduleEnabled) {
            return;
        }
        boolean mockData = "Mock Data".equals(
                dataSources.apiConnectionStatus().getApiConnectionStatus().displayName()
        );
        List<FlipOpportunity> opportunities = dataSources.opportunities().getOpportunities();
        if (!mockData) {
            clipboardTracker.copyFirstNew(opportunities);
        }
        NotificationSettings settings = dataSources.notificationSettings().getNotificationSettings();
        notificationsEnabled = settings.enabled();
        animationsEnabled = settings.animationsEnabled();
        if (!notificationsEnabled) {
            alerts.clear();
            executionNotice = null;
            return;
        }
        AutomationSettingsSnapshot automation = dataSources.automationSettings().snapshot();
        if (automation.configuration().showExecutionNotifications()) {
            String statusKey = automation.execution().activeState().map(Enum::name).orElse("IDLE")
                    + "|" + automation.execution().statusMessage();
            if (!statusKey.equals(lastExecutionStatus)
                    && !automation.execution().statusMessage().equals("Automation is disarmed.")) {
                Instant queuedAt = clock.instant();
                executionNotice = new ExecutionNotice(
                        automation.execution().activeState().map(Enum::name).orElse("AUTOMATION"),
                        automation.execution().statusMessage(), queuedAt, queuedAt.plusSeconds(5)
                );
                lastExecutionStatus = statusKey;
            }
        }
        if (mockData) {
            return;
        }
        Instant now = clock.instant();
        for (int index = 0; index < Math.min(opportunities.size(), OpportunityAlertQueue.MAXIMUM_QUEUED_ALERTS); index++) {
            alerts.offer(opportunities.get(index), now);
        }
    }

    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (!moduleEnabled || !notificationsEnabled || client.screen instanceof ClickGuiScreen) {
            return;
        }
        Instant now = clock.instant();
        boolean opportunityVisible = alerts.current(now)
                .map(alert -> {
                    draw(context, client.font, alert, now, animationsEnabled);
                    return true;
                }).orElse(false);
        ExecutionNotice execution = executionNotice;
        if (execution != null && execution.expiresAt().isAfter(now)) {
            drawExecution(context, client.font, execution, now,
                    animationsEnabled, opportunityVisible ? 76 : 18);
        }
    }

    public int queuedAlertCount() {
        return alerts.size(clock.instant());
    }

    public boolean dismissCurrent() {
        return alerts.dismissCurrent();
    }

    private static void draw(
            GuiGraphics context,
            Font renderer,
            OpportunityAlertQueue.QueuedAlert alert,
            Instant now,
            boolean animationsEnabled
    ) {
        FlipOpportunity value = alert.opportunity();
        float opacity = fadeOpacity(alert.queuedAt(), now, animationsEnabled);
        if (opacity <= 0.0F) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        int x = Math.max(6, client.getWindow().getGuiScaledWidth() - WIDTH - 8);
        int y = 18;
        GuiDraw.fillVerticalGradient(context, x + 1, y + 1, WIDTH - 2, HEIGHT - 2,
                withOpacity(GuiColors.NOTIFICATION_GRADIENT_TOP, opacity),
                withOpacity(GuiColors.NOTIFICATION_GRADIENT_BOTTOM, opacity));
        GuiDraw.strokeRect(context, x, y, WIDTH, HEIGHT,
                withOpacity(GuiColors.NOTIFICATION_BORDER, opacity));
        GuiTypography.draw(context, renderer, "Opportunity Found", x + 8, y + 7,
                withOpacity(GuiColors.PRIMARY_TEXT, opacity), false);
        GuiTypography.draw(context, renderer, fit(renderer, value.itemName() + " x" + value.count(), WIDTH - 16),
                x + 8, y + 21, withOpacity(GuiColors.SECONDARY_TEXT, opacity), false);
        String metrics = "Buy $" + compact(value.listingPrice()) + "  Value $" + compact(value.fairValue())
                + "  ROI " + String.format(Locale.ROOT, "%.0f%%", value.roiPercent());
        GuiTypography.draw(context, renderer, fit(renderer, metrics, WIDTH - 16), x + 8, y + 35,
                withOpacity(GuiColors.SECONDARY_TEXT, opacity), false);
    }

    private static void drawExecution(
            GuiGraphics context,
            Font renderer,
            ExecutionNotice notice,
            Instant now,
            boolean animationsEnabled,
            int y
    ) {
        float opacity = fadeOpacity(notice.queuedAt(), now, animationsEnabled);
        Minecraft client = Minecraft.getInstance();
        int x = Math.max(6, client.getWindow().getGuiScaledWidth() - WIDTH - 8);
        GuiDraw.fillVerticalGradient(context, x + 1, y + 1, WIDTH - 2, HEIGHT - 2,
                withOpacity(GuiColors.NOTIFICATION_GRADIENT_TOP, opacity),
                withOpacity(GuiColors.NOTIFICATION_GRADIENT_BOTTOM, opacity));
        GuiDraw.strokeRect(context, x, y, WIDTH, HEIGHT,
                withOpacity(GuiColors.NOTIFICATION_BORDER, opacity));
        GuiTypography.draw(context, renderer, "Automation: " + fit(renderer, notice.state(), 106),
                x + 8, y + 8, withOpacity(GuiColors.PRIMARY_TEXT, opacity), false);
        GuiTypography.draw(context, renderer, fit(renderer, notice.message(), WIDTH - 16),
                x + 8, y + 24, withOpacity(GuiColors.SECONDARY_TEXT, opacity), false);
        GuiTypography.draw(context, renderer, "Authorization gates remain active",
                x + 8, y + 38, withOpacity(GuiColors.MUTED_TEXT, opacity), false);
    }

    private static String fit(Font renderer, String value, int maximumWidth) {
        if (GuiTypography.width(renderer, value) <= maximumWidth) {
            return value;
        }
        String suffix = "...";
        int end = value.length();
        while (end > 0 && GuiTypography.width(renderer, value.substring(0, end) + suffix) > maximumWidth) {
            end--;
        }
        return value.substring(0, end) + suffix;
    }

    static float fadeOpacity(Instant queuedAt, Instant now, boolean animationsEnabled) {
        if (!animationsEnabled) {
            return 1.0F;
        }
        long elapsed = Math.max(0L, Duration.between(queuedAt, now).toNanos());
        float progress = Math.min(1.0F, elapsed / (float) FADE_IN_NANOS);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = color >>> 24 & 0xFF;
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round(alpha * opacity)));
        return adjustedAlpha << 24 | color & 0x00FFFFFF;
    }

    private static String compact(long value) {
        long absolute = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        if (absolute >= 1_000_000_000L) {
            return decimal(value / 1_000_000_000.0D) + "B";
        }
        if (absolute >= 1_000_000L) {
            return decimal(value / 1_000_000.0D) + "M";
        }
        if (absolute >= 1_000L) {
            return decimal(value / 1_000.0D) + "K";
        }
        return Long.toString(value);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, Math.abs(value) >= 100.0D ? "%.0f" : "%.1f", value)
                .replace(".0", "");
    }

    private record ExecutionNotice(String state, String message, Instant queuedAt, Instant expiresAt) {
    }
}
