package com.scmarket.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalActionRecorderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesOnlyBoundedGuiClickMetadataToTheLocalDiagnosticsFolder() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC);
        LocalActionRecorder recorder = new LocalActionRecorder(temporaryDirectory, fixedClock);
        Path output = recorder.start(100).orElseThrow();
        recorder.recordGuiClick("Completed Orders", 7, 22, "minecraft:diamond", 64, 0, 91, 44);
        recorder.stop();

        assertTrue(output.startsWith(temporaryDirectory.resolve("diagnostics")));
        String jsonl = Files.readString(output);
        assertTrue(jsonl.contains("capture_started"));
        assertTrue(jsonl.contains("gui_click"));
        assertTrue(jsonl.contains("Completed Orders"));
        assertTrue(jsonl.contains("minecraft:diamond"));
        assertTrue(jsonl.contains("\"screenX\":91"));
        assertTrue(jsonl.contains("\"screenY\":44"));
        assertFalse(jsonl.contains("chatMessage"));
        assertFalse(recorder.isRecording());
    }

    @Test
    void validatesCaptureBoundBeforeCreatingOutput() {
        LocalActionRecorder recorder = new LocalActionRecorder(temporaryDirectory);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> recorder.start(99));
    }
}
