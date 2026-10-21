package com.scmarket.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Atomic, bounded persistence for the global switch and independent module switches. */
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
            if (Files.isSymbolicLink(path) || Files.size(path) > 256) {
                return ModRuntimeSettings.defaults();
            }
            String[] lines = Files.readString(path, StandardCharsets.US_ASCII).split("\\R");
            if (lines.length != 2 || !(lines[0].equals("enabled=true") || lines[0].equals("enabled=false"))) {
                return ModRuntimeSettings.defaults();
            }
            boolean enabled = Boolean.parseBoolean(lines[0].substring("enabled=".length()));
            if (lines[1].startsWith("modules=")) {
                return new ModRuntimeSettings(enabled, parseModules(lines[1].substring("modules=".length())));
            }
            // Read the previous two-line `mode=` format without losing the user's chosen workflow.
            if (lines[1].startsWith("mode=")) {
                return new ModRuntimeSettings(enabled,
                        OperatingMode.valueOf(lines[1].substring("mode=".length())));
            }
            return ModRuntimeSettings.defaults();
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
                String modules = settings.activeModules().stream().sorted()
                        .map(Enum::name).collect(Collectors.joining(","));
                Files.writeString(temporary,
                        "enabled=" + settings.enabled() + "\nmodules=" + modules + "\n",
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

    private static Set<MarketModule> parseModules(String serialized) {
        if (serialized.isBlank()) {
            return Set.of();
        }
        EnumSet<MarketModule> modules = EnumSet.noneOf(MarketModule.class);
        for (String token : serialized.split(",", -1)) {
            if (token.isBlank() || !modules.add(MarketModule.valueOf(token))) {
                throw new IllegalArgumentException("Invalid or duplicate module name");
            }
        }
        return modules;
    }
}
