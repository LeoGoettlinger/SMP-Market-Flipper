package com.example.donutflipscanner.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Small atomic preference file kept separate from credentials and market data. */
public final class MarketDetectionModeStore {
    private final Path path;

    public MarketDetectionModeStore(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    public MarketDetectionMode load(MarketDetectionMode defaultMode) {
        Objects.requireNonNull(defaultMode, "defaultMode");
        if (!Files.exists(path)) {
            return defaultMode;
        }
        try {
            if (Files.isSymbolicLink(path) || Files.size(path) > 32) {
                return defaultMode;
            }
            return MarketDetectionMode.valueOf(Files.readString(path, StandardCharsets.US_ASCII).strip());
        } catch (IOException | IllegalArgumentException ignored) {
            return defaultMode;
        }
    }

    public void save(MarketDetectionMode mode) {
        Objects.requireNonNull(mode, "mode");
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalStateException("Detection mode preference path has no parent.");
        }
        try {
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, "market-mode-", ".tmp");
            boolean moved = false;
            try {
                Files.writeString(temporary, mode.name() + System.lineSeparator(), StandardCharsets.US_ASCII,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
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
            throw new IllegalStateException("Unable to save detection mode preference.", error);
        }
    }
}
