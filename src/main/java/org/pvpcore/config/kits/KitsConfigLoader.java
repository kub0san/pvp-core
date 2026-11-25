package org.pvpcore.config.kits;

import org.pvpcore.config.ConfigFiles;
import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class KitsConfigLoader {
    public record LoadedConfig(YamlConfiguration config, Path file) {}

    private final Plugin plugin;

    public KitsConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Loads and validates kits/config.yml, returning the loaded config and path on success. */
    public LoadedConfig load() {
        Path folder = migrateDataFolder();
        Path file = folder.resolve("config.yml");
        try {
            Files.createDirectories(folder);
            if (!Files.exists(file)) {
                // save default from bundled resource
                try (InputStream in = plugin.getResource("kits/config.yml")) {
                    if (in == null) throw new java.io.IOException("Bundled kits/config.yml is missing");
                    Files.copy(in, file);
                }
            }
            YamlConfiguration config = ConfigFiles.read(file);
            KitRoomDataManager.configuredPages(config);
            ItemFilterRules.parse(config);
            org.pvpcore.util.kits.LocationRules.parse(config);
            org.pvpcore.util.kits.RekitKitResolver.validate(config);
            CustomizeRules.parse(config.getConfigurationSection("customize"),
                    warning -> plugin.getLogger().warning(warning));
            try (InputStream in = plugin.getResource("kits/config.yml")) {
                if (in == null) throw new java.io.IOException("Bundled kits/config.yml is missing");
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                boolean changed = false;
                for (String key : defaults.getKeys(true)) {
                    if (key.startsWith("publickits.") || defaults.isConfigurationSection(key)) continue;
                    if (!config.contains(key)) {
                        config.set(key, defaults.get(key));
                        changed = true;
                    }
                }
                if (!config.contains("publickits")) {
                    config.set("publickits", defaults.get("publickits"));
                    changed = true;
                }
                if (changed) ConfigFiles.write(config, file);
            }
            return new LoadedConfig(config, file);
        } catch (Exception e) {
            plugin.getLogger().severe("Unable to load kits/config.yml: " + e.getMessage());
            return null;
        }
    }

    /** @deprecated Use {@link #load()} and apply to the active kits context/module. */
    @Deprecated
    public boolean loadConfig() {
        LoadedConfig loaded = load();
        if (loaded == null) return false;
        org.pvpcore.features.kits.Kits.setConfig(loaded.config());
        org.pvpcore.features.kits.Kits.setConfigFile(loaded.file());
        return true;
    }

    private Path migrateDataFolder() {
        Path current = plugin.getDataFolder().toPath().resolve("kits");
        Path legacy = plugin.getDataFolder().toPath().resolve("Kits");
        if (!Files.exists(current) && Files.exists(legacy)) {
            try {
                Files.move(legacy, current);
                plugin.getLogger().info("Migrated data folder: Kits -> kits");
            } catch (Exception e) {
                plugin.getLogger().warning("Found legacy data folder 'Kits' but could not rename it to 'kits': "
                        + e.getMessage() + ". Using legacy folder for compatibility.");
                return legacy;
            }
        }
        return current;
    }
}