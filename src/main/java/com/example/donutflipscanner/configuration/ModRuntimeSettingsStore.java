package com.example.donutflipscanner.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Atomic, bounded persistence for the mod-wide enable and workflow switches. */
public final class ModRuntimeSettingsStore {
    private final Path path;

    public ModRuntimeSettingsStore(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    public ModRuntimeSettings load() {
        if (!Files.exists(path)) {
            return ModRuntimeSettings.defaults();
        }
        try {
            if (Files.isSymbolicLink(path) || Files.size(path) > 128) {
                return ModRuntimeSettings.defaults();
            }
            String[] lines = Files.readString(path, StandardCharsets.US_ASCII).split("\\R");
            if (lines.length != 2 || !(lines[0].equals("enabled=true") || lines[0].equals("enabled=false"))) {
                return ModRuntimeSettings.defaults();
            }
            return new ModRuntimeSettings(Boolean.parseBoolean(lines[0].substring("enabled=".length())),
                    OperatingMode.valueOf(lines[1].substring("mode=".length())));
        } catch (IOException | RuntimeException invalid) {
            return ModRuntimeSettings.defaults();
        }
    }

    public void save(ModRuntimeSettings settings) {
        Objects.requireNonNull(settings, "settings");
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalStateException("Runtime settings path has no parent.");
        }
        try {
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, "runtime-settings-", ".tmp");
            boolean moved = false;
            try {
                Files.writeString(temporary,
                        "enabled=" + settings.enabled() + "\nmode=" + settings.mode().name() + "\n",
                        StandardCharsets.US_ASCII, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
                moved = true;
            } finally {
                if (!moved) {
                    Files.deleteIfExists(temporary);
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to save runtime settings.", error);
        }
    }
}
