package com.scmarket.rtp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

/** Local persistent settings for the opt-in RTP bot. */
public final class RtpBotSettingsStore {
    public static final int DEFAULT_DELAY_SECONDS = 60;
    public static final int MINIMUM_DELAY_SECONDS = 30;
    public static final int MAXIMUM_DELAY_SECONDS = 600;

    private final Path file;

    public RtpBotSettingsStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public int loadDelaySeconds() throws IOException {
        if (!Files.exists(file)) {
            return DEFAULT_DELAY_SECONDS;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        try {
            int value = Integer.parseInt(properties.getProperty("delaySeconds", ""));
            return isValid(value) ? value : DEFAULT_DELAY_SECONDS;
        } catch (NumberFormatException invalidValue) {
            return DEFAULT_DELAY_SECONDS;
        }
    }

    public void saveDelaySeconds(int seconds) throws IOException {
        if (!isValid(seconds)) {
            throw new IllegalArgumentException("RTP delay must be between 30 and 600 seconds");
        }
        Path parent = file.getParent();
        if (parent == null) {
            throw new IOException("RTP settings file must have a parent directory");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "rtp-bot-", ".tmp");
        Properties properties = new Properties();
        properties.setProperty("delaySeconds", Integer.toString(seconds));
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "Local RTP bot settings; coordinates are stored separately.");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean isValid(int value) {
        return value >= MINIMUM_DELAY_SECONDS && value <= MAXIMUM_DELAY_SECONDS;
    }
}
