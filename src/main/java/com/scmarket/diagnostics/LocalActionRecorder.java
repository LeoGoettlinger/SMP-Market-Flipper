package com.scmarket.diagnostics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/** Opt-in, bounded JSONL capture for local GUI diagnosis. It never records chat, keys, or credentials. */
public final class LocalActionRecorder implements AutoCloseable {
    private static final int MAXIMUM_FILE_BYTES = 4 * 1024 * 1024;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);
    private final Path diagnosticsDirectory;
    private final Clock clock;
    private BufferedWriter writer;
    private Path activeFile;
    private int recordsWritten;
    private int maximumRecords;

    public LocalActionRecorder(Path configDirectory) {
        this(configDirectory, Clock.systemUTC());
    }

    LocalActionRecorder(Path configDirectory, Clock clock) {
        this.diagnosticsDirectory = Objects.requireNonNull(configDirectory, "configDirectory")
                .toAbsolutePath().normalize().resolve("diagnostics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Optional<Path> start(int maximumRecords) throws IOException {
        if (maximumRecords < 100 || maximumRecords > 10_000) {
            throw new IllegalArgumentException("maximumRecords must be between 100 and 10000");
        }
        stop();
        Files.createDirectories(diagnosticsDirectory);
        String base = "order-capture-" + FILE_TIME.format(clock.instant());
        Path candidate = diagnosticsDirectory.resolve(base + ".jsonl").normalize();
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = diagnosticsDirectory.resolve(base + "-" + suffix++ + ".jsonl").normalize();
        }
        if (!candidate.getParent().equals(diagnosticsDirectory)) {
            throw new IOException("diagnostic output escaped its local directory");
        }
        writer = Files.newBufferedWriter(candidate, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        activeFile = candidate;
        recordsWritten = 0;
        this.maximumRecords = maximumRecords;
        writeRecord("capture_started", "", -1, -1, "", 0, -1, -1, -1);
        return Optional.of(activeFile);
    }

    public synchronized void recordGuiClick(
            String screenTitle, int syncId, int slot, String itemId, int itemCount, int mouseButton
    ) {
        recordGuiClick(screenTitle, syncId, slot, itemId, itemCount, mouseButton, -1, -1);
    }

    public synchronized void recordGuiClick(
            String screenTitle, int syncId, int slot, String itemId, int itemCount,
            int mouseButton, int screenX, int screenY
    ) {
        if (writer == null || recordsWritten >= maximumRecords) {
            return;
        }
        writeRecord("gui_click", screenTitle, syncId, slot, itemId, itemCount,
                mouseButton, screenX, screenY);
    }

    public synchronized boolean isRecording() {
        return writer != null;
    }

    public synchronized Optional<Path> activeFile() {
        return Optional.ofNullable(activeFile);
    }

    public synchronized int recordsWritten() {
        return recordsWritten;
    }

    public synchronized void stop() throws IOException {
        if (writer == null) {
            activeFile = null;
            recordsWritten = 0;
            return;
        }
        BufferedWriter closing = writer;
        writer = null;
        try {
            closing.flush();
            closing.close();
        } finally {
            activeFile = null;
            recordsWritten = 0;
        }
    }

    @Override
    public void close() {
        try {
            stop();
        } catch (IOException ignored) {
            // Shutdown is best effort; no gameplay operation depends on diagnostics.
        }
    }

    private void writeRecord(
            String event, String title, int syncId, int slot, String itemId,
            int count, int button, int screenX, int screenY
    ) {
        if (writer == null) {
            return;
        }
        try {
            String json = "{\"time\":\"" + escape(Instant.now(clock).toString())
                    + "\",\"event\":\"" + escape(event)
                    + "\",\"screen\":\"" + escape(bounded(title, 120))
                    + "\",\"syncId\":" + syncId
                    + ",\"slot\":" + slot
                    + ",\"itemId\":\"" + escape(bounded(itemId, 256))
                    + "\",\"count\":" + Math.max(0, Math.min(count, 99_999))
                    + ",\"button\":" + button
                    + ",\"screenX\":" + boundedCoordinate(screenX)
                    + ",\"screenY\":" + boundedCoordinate(screenY) + "}";
            long encodedLength = json.getBytes(StandardCharsets.UTF_8).length + 1L;
            if (encodedLength > MAXIMUM_FILE_BYTES
                    || Files.exists(activeFile) && Files.size(activeFile) + encodedLength > MAXIMUM_FILE_BYTES) {
                stop();
                return;
            }
            writer.write(json);
            writer.newLine();
            recordsWritten++;
            if (recordsWritten >= maximumRecords) {
                writer.flush();
                writer.close();
                writer = null;
            }
        } catch (IOException failure) {
            BufferedWriter failed = writer;
            writer = null;
            activeFile = null;
            recordsWritten = 0;
            if (failed != null) {
                try {
                    failed.close();
                } catch (IOException ignored) {
                    // Recording is best-effort and must never interrupt gameplay.
                }
            }
        }
    }

    private static int boundedCoordinate(int value) {
        return Math.max(-1, Math.min(value, 16_384));
    }

    private static String bounded(String value, int maximumLength) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').strip();
        return cleaned.length() <= maximumLength ? cleaned : cleaned.substring(0, maximumLength);
    }

    private static String escape(String value) {
        StringBuilder output = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        return output.toString();
    }
}
