package com.scmarket.rtp;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtpHistoryStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void appendsLocallyAndRestoresHistoryAfterRestart() throws Exception {
        Path file = temporaryDirectory.resolve("config/sc-market/rtp-history.jsonl");
        RtpHistoryStore first = new RtpHistoryStore(file);
        first.append(new RtpLocation(Instant.parse("2026-10-24T10:00:00Z").toEpochMilli(),
                "minecraft:overworld", 123, 72, -456, RtpLocation.Source.RTP_BOT));
        first.append(new RtpLocation(Instant.parse("2026-10-24T10:01:00Z").toEpochMilli(),
                "minecraft:the_nether", -80, 64, 91, RtpLocation.Source.MANUAL));

        RtpHistoryStore restarted = new RtpHistoryStore(file);

        assertEquals(2, restarted.totalRecords());
        assertEquals("minecraft:the_nether", restarted.latest().orElseThrow().dimension());
        assertEquals(-80, restarted.latest().orElseThrow().x());
    }

    @Test
    void malformedRowsDoNotEraseOtherSavedLandingPoints() throws Exception {
        Path file = temporaryDirectory.resolve("rtp-history.jsonl");
        RtpHistoryStore store = new RtpHistoryStore(file);
        store.append(new RtpLocation(1_797_000_000_000L, "minecraft:overworld", 1, 70, 2,
                RtpLocation.Source.MANUAL));
        java.nio.file.Files.writeString(file, "not-json\n", java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);

        RtpHistoryStore restarted = new RtpHistoryStore(file);

        assertEquals(1, restarted.totalRecords());
        assertEquals(1, restarted.latest().orElseThrow().x());
    }
}
