package org.pvpcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public final class CoreConfigLoader {

    private CoreConfigLoader() {
    }

    public record LoadResult(FileConfiguration config, boolean changed) {
    }

    public static FileConfiguration load(JavaPlugin plugin, String relativePath) {
        return loadWithStatus(plugin, relativePath, List.of()).config();
    }

    public static FileConfiguration load(JavaPlugin plugin, String relativePath, List<String> removedKeys) {
        return loadWithStatus(plugin, relativePath, removedKeys).config();
    }

    public static LoadResult loadWithStatus(JavaPlugin plugin, String relativePath, List<String> removedKeys) {
        File file = new File(plugin.getDataFolder(), relativePath);
        boolean existed = file.exists();
        if (!existed) {
            plugin.saveResource(relativePath, false);
        }

        Path path = file.toPath();
        YamlConfiguration config;
        try {
            config = ConfigFiles.read(path);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not read " + relativePath + ": " + e.getMessage(), e);
        }

        boolean changed = !existed;

        for (String key : removedKeys) {
            if (config.contains(key)) {
                config.set(key, null);
                changed = true;
            }
        }

        try (InputStream stream = plugin.getResource(relativePath)) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                for (String key : defaults.getKeys(true)) {
                    if (defaults.isConfigurationSection(key)) continue;
                    if (!config.contains(key)) {
                        config.set(key, defaults.get(key));
                        changed = true;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read default " + relativePath + " from jar: " + e.getMessage());
        }

        if (changed) {
            try {
                ConfigFiles.write(config, path);
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("Could not save updated " + relativePath + ": " + e.getMessage());
            }
        }

        return new LoadResult(config, changed);
    }

    public static void save(JavaPlugin plugin, String relativePath, FileConfiguration config) {
        File file = new File(plugin.getDataFolder(), relativePath);
        try {
            ConfigFiles.write(config, file.toPath());
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not save " + relativePath + ": " + e.getMessage());
        }
    }
}