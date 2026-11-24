package org.pvpcore.features.kits;

import org.pvpcore.config.ConfigFiles;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * One-time import of a pre-merge PerPlayerKit installation.
 *
 * <p>The old plugin stored its files in {@code plugins/PerPlayerKit/}. The merged Kits module
 * reads {@code plugins/PvPCore/Kits/config.yml} instead, so an operator upgrading in place would
 * otherwise start with a fresh, empty configuration. This copies the old config (and the files
 * that belong to it: the English message file, starter defaults, the SQLite/YAML database and
 * backups) into the new layout only when the new config does not exist yet. Existing new-style
 * configs are never touched.
 */

public final class LegacyKitsImport {

    private static final String LEGACY_FOLDER = "PerPlayerKit";
    private static final String SQLITE_FILE = "database.db";
    private static final String YAML_FILE = "please-use-a-real-database.yml";

    private LegacyKitsImport() {
    }

    /** Returns true when a legacy installation was imported. */

    public static boolean importIfNeeded(Plugin plugin) {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path kitsFolder = dataFolder.resolve("kits");
        Path legacyKitsFolder = dataFolder.resolve("Kits");
        Path newConfig = kitsFolder.resolve("config.yml");
        Path legacyNewConfig = legacyKitsFolder.resolve("config.yml");
        if (Files.exists(newConfig) || Files.exists(legacyNewConfig)) {
            return false;
        }

        Path legacyFolder = dataFolder.getParent() == null
                ? dataFolder.resolve(LEGACY_FOLDER)
                : dataFolder.getParent().resolve(LEGACY_FOLDER);
        Path legacyConfig = legacyFolder.resolve("config.yml");
        if (!Files.isRegularFile(legacyConfig)) {
            return false;
        }

        try {
            Files.createDirectories(kitsFolder);

            YamlConfiguration config = ConfigFiles.read(legacyConfig);
            // The merged plugin has a master switch that the standalone plugin did not have,
            // and messages now live in Kits/messages.yml instead of a selectable language file.
            if (!config.contains("enabled")) {
                config.set("enabled", true);
            }
            config.set("language", null);
            ConfigFiles.write(config, newConfig);

            copyMessages(legacyFolder, kitsFolder);
            copyDefaults(legacyFolder, kitsFolder);
            copyStorageFiles(legacyFolder, dataFolder, config);

            plugin.getLogger().info("Imported legacy PerPlayerKit configuration from "
                    + legacyConfig + " to " + newConfig + ".");
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not import the legacy PerPlayerKit configuration: "
                    + e.getMessage() + ". A fresh Kits/config.yml will be created instead.");
            return false;
        }
    }

    private static void copyMessages(Path legacyFolder, Path kitsFolder) throws IOException {
        Path legacyEnglish = legacyFolder.resolve("lang").resolve("en.yml");
        if (Files.isRegularFile(legacyEnglish)) {
            copyIfMissing(legacyEnglish, kitsFolder.resolve("messages.yml"));
        }
    }

    private static void copyDefaults(Path legacyFolder, Path kitsFolder) throws IOException {
        Path legacyDefaults = legacyFolder.resolve("defaults");
        if (!Files.isDirectory(legacyDefaults)) {
            return;
        }
        Path newDefaults = kitsFolder.resolve("defaults");
        Files.createDirectories(newDefaults);
        try (Stream<Path> files = Files.list(legacyDefaults)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".yml")) {
                    continue;
                }
                copyIfMissing(file, newDefaults.resolve(file.getFileName().toString()));
            }
        }
    }

    private static void copyStorageFiles(Path legacyFolder, Path dataFolder, YamlConfiguration config)
            throws IOException {
        String storageType = config.getString("storage.type", "sqlite");
        if (storageType == null) {
            storageType = "sqlite";
        }

        switch (storageType.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "sqlite" -> copyIfMissing(legacyFolder.resolve(SQLITE_FILE), dataFolder.resolve(SQLITE_FILE));
            case "yml", "yaml" -> copyIfMissing(legacyFolder.resolve(YAML_FILE), dataFolder.resolve(YAML_FILE));
            default -> {
                // Remote databases keep their data on the database server, so there is nothing to copy.
            }
        }

        Path legacyBackups = legacyFolder.resolve("backups");
        if (Files.isDirectory(legacyBackups)) {
            copyDirectoryIfMissing(legacyBackups, dataFolder.resolve("backups"));
        }
    }

    private static void copyIfMissing(Path source, Path target) throws IOException {
        if (Files.isRegularFile(source) && !Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void copyDirectoryIfMissing(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Path relative = source.relativize(file);
                Path destination = target.resolve(relative.toString());
                if (!Files.exists(destination)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination);
                }
            }
        }
    }
}
