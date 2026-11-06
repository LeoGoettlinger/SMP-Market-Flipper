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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Opt-in, bounded JSONL capture for local GUI diagnosis. It never records chat, keys, or credentials. */
public final class LocalActionRecorder implements AutoCloseable {
    private static final int MAXIMUM_FILE_BYTES = 4 * 1024 * 1024;
    private static final int MAXIMUM_LORE_LINES = 32;
    private static final int MAXIMUM_LORE_LINE_LENGTH = 320;
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
        writeRecord("capture_started", "", -1, -1, "", 0, -1, -1, -1,
                "", List.of(), -1, -1, -1, -1, -1);
        return Optional.of(activeFile);
    }

    /** Records a screen/menu snapshot header before its individual slot_snapshot rows. */
    public synchronized void recordScreenSnapshot(
            String screenTitle, int syncId, int screenWidth, int screenHeight, int slotCount
    ) {
        if (!canRecord()) {
            return;
        }
        writeRecord("screen_snapshot", screenTitle, syncId, -1, "", 0, -1, -1, -1,
                "", List.of(), screenWidth, screenHeight, slotCount, -1, -1);
    }

    /** Records a stack's visible identity and lore; lore may include seller names, so files stay local. */
    public synchronized void recordSlotSnapshot(
            String screenTitle, int syncId, int slot, String itemId, int itemCount,
            String displayName, List<String> lore, int screenWidth, int screenHeight, int slotX, int slotY
    ) {
        if (!canRecord()) {
            return;
        }
        writeRecord("slot_snapshot", screenTitle, syncId, slot, itemId, itemCount, -1, -1, -1,
                displayName, lore, screenWidth, screenHeight, -1, slotX, slotY);
    }

    public synchronized void recordGuiClick(
            String screenTitle, int syncId, int slot, String itemId, int itemCount, int mouseButton
    ) {
        recordGuiClick(screenTitle, syncId, slot, itemId, itemCount, "", List.of(),
                mouseButton, -1, -1);
    }

    public synchronized void recordGuiClick(
            String screenTitle, int syncId, int slot, String itemId, int itemCount,
            int mouseButton, int screenX, int screenY
    ) {
        recordGuiClick(screenTitle, syncId, slot, itemId, itemCount, "", List.of(),
                mouseButton, screenX, screenY);
    }

    public synchronized void recordGuiClick(
            String screenTitle, int syncId, int slot, String itemId, int itemCount,
            String displayName, List<String> lore, int mouseButton, int screenX, int screenY
    ) {
        if (!canRecord()) {
            return;
        }
        writeRecord("gui_click", screenTitle, syncId, slot, itemId, itemCount,
                mouseButton, screenX, screenY, displayName, lore, -1, -1, -1, -1, -1);
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

    private boolean canRecord() {
        return writer != null && recordsWritten < maximumRecords;
    }

    private void writeRecord(
            String event, String title, int syncId, int slot, String itemId,
            int count, int button, int screenX, int screenY, String displayName,
            List<String> lore, int screenWidth, int screenHeight, int slotCount, int slotX, int slotY
    ) {
        if (writer == null) {
            return;
        }
        try {
            StringBuilder json = new StringBuilder(256)
                    .append("{\"time\":\"").append(escape(Instant.now(clock).toString()))
                    .append("\",\"event\":\"").append(escape(event))
                    .append("\",\"screen\":\"").append(escape(bounded(title, 120)))
                    .append("\",\"syncId\":").append(syncId)
                    .append(",\"slot\":").append(slot)
                    .append(",\"itemId\":\"").append(escape(bounded(itemId, 256)))
                    .append("\",\"count\":").append(Math.max(0, Math.min(count, 99_999)))
                    .append(",\"displayName\":\"").append(escape(bounded(displayName, 256)))
                    .append("\",\"lore\":[");
            List<String> safeLore = boundedLore(lore);
            for (int index = 0; index < safeLore.size(); index++) {
                if (index > 0) {
                    json.append(',');
                }
                json.append('\"').append(escape(safeLore.get(index))).append('\"');
            }
            json.append("],\"button\":").append(button)
                    .append(",\"screenX\":").append(boundedCoordinate(screenX))
                    .append(",\"screenY\":").append(boundedCoordinate(screenY))
                    .append(",\"screenWidth\":").append(boundedCoordinate(screenWidth))
                    .append(",\"screenHeight\":").append(boundedCoordinate(screenHeight))
                    .append(",\"slotCount\":").append(Math.max(-1, Math.min(slotCount, 512)))
                    .append(",\"slotX\":").append(boundedCoordinate(slotX))
                    .append(",\"slotY\":").append(boundedCoordinate(slotY))
                    .append('}');
            String line = json.toString();
            long encodedLength = line.getBytes(StandardCharsets.UTF_8).length + 1L;
            if (encodedLength > MAXIMUM_FILE_BYTES
                    || Files.exists(activeFile) && Files.size(activeFile) + encodedLength > MAXIMUM_FILE_BYTES) {
                stop();
                return;
            }
            writer.write(line);
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

    private static List<String> boundedLore(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        return lore.stream().limit(MAXIMUM_LORE_LINES)
                .map(line -> bounded(line, MAXIMUM_LORE_LINE_LENGTH)).toList();
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
