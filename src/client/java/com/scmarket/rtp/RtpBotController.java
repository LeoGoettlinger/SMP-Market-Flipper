package com.scmarket.rtp;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Explicitly armed, allowlist-gated /rtp loop that records only confirmed landing changes. */
public final class RtpBotController {
    private static final long TELEPORT_CONFIRM_TIMEOUT_NANOS = 30_000_000_000L;
    private static final double MINIMUM_LANDING_DISTANCE_SQUARED = 64.0D * 64.0D;

    private final Minecraft client;
    private final BooleanSupplier authorized;
    private final RtpHistoryStore history;
    private boolean running;
    private boolean awaitingLanding;
    private int delaySeconds = 60;
    private long nextCommandNanos;
    private long landingDeadlineNanos;
    private double originX;
    private double originY;
    private double originZ;
    private String originDimension = "";
    private String status = "RTP bot is stopped.";

    public RtpBotController(Minecraft client, BooleanSupplier authorized, RtpHistoryStore history) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
        this.history = Objects.requireNonNull(history, "history");
    }

    public void start() {
        if (!isAuthorized()) {
            running = false;
            status = "RTP bot blocked: allowlist this exact server first.";
            return;
        }
        running = true;
        awaitingLanding = false;
        nextCommandNanos = System.nanoTime();
        status = "RTP bot armed; waiting to send /rtp.";
    }

    public void stop() {
        running = false;
        awaitingLanding = false;
        status = "RTP bot is stopped.";
    }

    public boolean recordCurrentPosition() {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.getConnection() == null) {
            status = "No current world position to save.";
            return false;
        }
        try {
            history.append(location(player, RtpLocation.Source.MANUAL));
            status = "Current RTP saved locally.";
            return true;
        } catch (IOException | RuntimeException failure) {
            status = "Could not save the RTP history; check the local config folder.";
            return false;
        }
    }

    public void setDelaySeconds(int seconds) {
        if (seconds < 30 || seconds > 600) {
            throw new IllegalArgumentException("RTP delay must be between 30 and 600 seconds");
        }
        delaySeconds = seconds;
    }

    public void tick() {
        if (!running) {
            return;
        }
        if (!isAuthorized()) {
            stop();
            status = "RTP bot stopped because the exact-server allowlist no longer passes.";
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.getConnection() == null) {
            status = "RTP bot paused: not connected to a world.";
            return;
        }
        long now = System.nanoTime();
        if (awaitingLanding) {
            if (hasLanded(player)) {
                try {
                    history.append(location(player, RtpLocation.Source.RTP_BOT));
                    awaitingLanding = false;
                    nextCommandNanos = now + delaySeconds * 1_000_000_000L;
                    status = "RTP landing saved locally; next attempt in " + delaySeconds + " seconds.";
                } catch (IOException | RuntimeException failure) {
                    stop();
                    status = "RTP bot stopped because the local landing could not be saved.";
                }
            } else if (now >= landingDeadlineNanos) {
                awaitingLanding = false;
                nextCommandNanos = now + delaySeconds * 1_000_000_000L;
                status = "No landing was detected after /rtp; waiting before the next attempt.";
            }
            return;
        }
        if (client.gui.screen() != null || now < nextCommandNanos) {
            return;
        }
        originX = player.getX();
        originY = player.getY();
        originZ = player.getZ();
        originDimension = dimensionId(client.level.dimension());
        try {
            client.getConnection().sendCommand("rtp");
            awaitingLanding = true;
            landingDeadlineNanos = now + TELEPORT_CONFIRM_TIMEOUT_NANOS;
            status = "Sent /rtp; waiting for the landing to change.";
        } catch (RuntimeException failure) {
            stop();
            status = "RTP bot stopped because /rtp could not be sent.";
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int delaySeconds() {
        return delaySeconds;
    }

    public long recordedCount() {
        return history.totalRecords();
    }

    public String status() {
        return status;
    }

    public RtpHistoryStore history() {
        return history;
    }

    private boolean isAuthorized() {
        try {
            return authorized.getAsBoolean();
        } catch (RuntimeException unavailableAuthorization) {
            return false;
        }
    }

    private boolean hasLanded(LocalPlayer player) {
        String currentDimension = dimensionId(client.level.dimension());
        if (!originDimension.equals(currentDimension)) {
            return true;
        }
        double dx = player.getX() - originX;
        double dy = player.getY() - originY;
        double dz = player.getZ() - originZ;
        return dx * dx + dy * dy + dz * dz >= MINIMUM_LANDING_DISTANCE_SQUARED;
    }

    private static RtpLocation location(LocalPlayer player, RtpLocation.Source source) {
        ResourceKey<Level> dimension = player.level().dimension();
        return new RtpLocation(Instant.now().toEpochMilli(), dimensionId(dimension),
                player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ(), source);
    }

    private static String dimensionId(ResourceKey<Level> dimension) {
        return dimension.identifier().toString();
    }
}
