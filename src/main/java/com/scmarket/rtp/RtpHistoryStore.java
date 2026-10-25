package com.scmarket.rtp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Append-only, local JSONL history of the player's own RTP landing points. */
public final class RtpHistoryStore {
    private static final int RECENT_LIMIT = 20;
    private final Path file;
    private final ArrayDeque<RtpLocation> recent = new ArrayDeque<>();
    private long totalRecords;

    public RtpHistoryStore(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        loadExisting();
    }

    public synchronized void append(RtpLocation location) throws IOException {
        Objects.requireNonNull(location, "location");
        Path parent = file.getParent();
        if (parent == null) {
            throw new IOException("RTP history file must have a parent directory");
        }
        Files.createDirectories(parent);
        String json = "{\"epochMillis\":" + location.epochMillis()
                + ",\"dimension\":\"" + escape(location.dimension())
                + "\",\"x\":" + location.x()
                + ",\"y\":" + location.y()
                + ",\"z\":" + location.z()
                + ",\"source\":\"" + location.source().name() + "\"}" + System.lineSeparator();
        Files.writeString(file, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        remember(location);
    }

    public synchronized long totalRecords() {
        return totalRecords;
    }

    public synchronized List<RtpLocation> recent() {
        return List.copyOf(recent);
    }

    public synchronized Optional<RtpLocation> latest() {
        return Optional.ofNullable(recent.peekLast());
    }

    public Path file() {
        return file;
    }

    private void loadExisting() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                    RtpLocation location = new RtpLocation(
                            json.get("epochMillis").getAsLong(),
                            json.get("dimension").getAsString(),
                            json.get("x").getAsInt(),
                            json.get("y").getAsInt(),
                            json.get("z").getAsInt(),
                            RtpLocation.Source.valueOf(json.get("source").getAsString()));
                    remember(location);
                } catch (RuntimeException malformedLine) {
                    // Keep earlier/newer valid entries; a damaged line must not erase the local history.
                }
            }
        }
    }

    private void remember(RtpLocation location) {
        recent.addLast(location);
        if (recent.size() > RECENT_LIMIT) {
            recent.removeFirst();
        }
        totalRecords++;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
