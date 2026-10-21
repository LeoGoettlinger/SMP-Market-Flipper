package com.scmarket.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDirectoryMigratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesLegacyDataWithoutRemovingSourceOrOverwritingNewerFiles() throws Exception {
        Path legacy = temporaryDirectory.resolve("donut-flip-scanner");
        Path current = temporaryDirectory.resolve("sc-market");
        Files.createDirectories(legacy.resolve("exports"));
        Files.createDirectories(current);
        Files.writeString(legacy.resolve("runtime-settings.txt"), "legacy settings");
        Files.writeString(legacy.resolve("exports/sales.json"), "legacy export");
        Files.writeString(current.resolve("runtime-settings.txt"), "new settings");

        ConfigDirectoryMigrator.migrate(temporaryDirectory,
                "donut-flip-scanner", "sc-market");

        assertEquals("legacy export", Files.readString(current.resolve("exports/sales.json")));
        assertEquals("new settings", Files.readString(current.resolve("runtime-settings.txt")));
        assertTrue(Files.exists(legacy.resolve("exports/sales.json")));
    }

    @Test
    void missingLegacyDirectoryIsANoOp() throws Exception {
        ConfigDirectoryMigrator.migrate(temporaryDirectory, "old", "sc-market");
        assertFalse(Files.exists(temporaryDirectory.resolve("sc-market")));
    }
}
