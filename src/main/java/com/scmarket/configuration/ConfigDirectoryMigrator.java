package com.scmarket.configuration;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/** Copies legacy user data into the SC-Market folder without deleting or overwriting anything. */
public final class ConfigDirectoryMigrator {
    private ConfigDirectoryMigrator() {
    }

    /**
     * Migrates only missing regular files. The old directory is intentionally retained as a backup,
     * and an existing file in the new directory always wins.
     */
    public static void migrate(Path configRoot, String legacyDirectoryName, String currentDirectoryName)
            throws IOException {
        Path root = Objects.requireNonNull(configRoot, "configRoot").toAbsolutePath().normalize();
        Path legacy = childDirectory(root, legacyDirectoryName);
        Path current = childDirectory(root, currentDirectoryName);
        if (legacy.equals(current) || !Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(legacy) || !Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Legacy configuration path is not a regular directory");
        }
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Current configuration path is not a regular directory");
        }
        Files.createDirectories(current);
        Files.walkFileTree(legacy, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path destination = current.resolve(legacy.relativize(directory)).normalize();
                if (!destination.startsWith(current)) {
                    throw new IOException("Legacy configuration path escapes its target directory");
                }
                Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) {
                    return FileVisitResult.CONTINUE;
                }
                Path destination = current.resolve(legacy.relativize(file)).normalize();
                if (!destination.startsWith(current)) {
                    throw new IOException("Legacy configuration path escapes its target directory");
                }
                if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Path childDirectory(Path root, String name) throws IOException {
        Objects.requireNonNull(name, "directoryName");
        Path child = root.resolve(name).normalize();
        if (!child.startsWith(root) || child.equals(root) || name.isBlank()) {
            throw new IOException("Configuration directory name is invalid");
        }
        return child;
    }
}
