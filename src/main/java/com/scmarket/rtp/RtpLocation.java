package com.scmarket.rtp;

import java.util.Objects;
import java.util.regex.Pattern;

/** Local-only record of one RTP landing; coordinates are never uploaded by this model. */
public record RtpLocation(long epochMillis, String dimension, int x, int y, int z, Source source) {
    private static final Pattern DIMENSION_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public RtpLocation {
        dimension = Objects.requireNonNull(dimension, "dimension").strip().toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(source, "source");
        if (epochMillis < 0 || dimension.length() > 128 || !DIMENSION_ID.matcher(dimension).matches()
                || Math.abs((long) x) > 30_000_000L || Math.abs((long) z) > 30_000_000L
                || y < -2_048 || y > 4_096) {
            throw new IllegalArgumentException("RTP location contains invalid or out-of-world coordinates");
        }
    }

    public enum Source {
        MANUAL,
        RTP_BOT
    }
}
