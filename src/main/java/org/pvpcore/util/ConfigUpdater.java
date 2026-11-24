package org.pvpcore.util;

import org.pvpcore.config.CoreConfigLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * Keeps every user-facing config file bundled with PvPCore in sync across updates.
 */
public final class ConfigUpdater {

    private static final List<ConfigSpec> BUNDLED_CONFIGS = List.of(
            new ConfigSpec("config.yml", List.of()),
            new ConfigSpec("messages.yml", List.of()),
            new ConfigSpec("kits/config.yml", List.of()),
            new ConfigSpec("teleport/config.yml", List.of()),
            new ConfigSpec("teleport/rtp.yml", List.of()),
            new ConfigSpec("worldgen/config.yml", List.of()),
            new ConfigSpec("combat/config.yml", List.of())
    );

    private ConfigUpdater() {
    }

    public static void updateAll(JavaPlugin plugin) {
        int touched = 0;
        for (ConfigSpec spec : BUNDLED_CONFIGS) {
            try {
                if (update(plugin, spec.path(), spec.removedKeys())) {
                    touched++;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning(
                        "Could not auto-update " + spec.path() + ": " + e.getMessage());
            }
        }
        if (touched > 0) {
            plugin.getLogger().info("Auto-updated " + touched + " of "
                    + BUNDLED_CONFIGS.size() + " bundled config file(s). ");
        }
    }

    public static boolean update(JavaPlugin plugin, String fileName) {
        return update(plugin, fileName, List.of());
    }

    public static boolean update(JavaPlugin plugin, String fileName, List<String> removedKeys) {
        File file = new File(plugin.getDataFolder(), fileName);
        boolean existed = file.exists();
        try {
            CoreConfigLoader.LoadResult result =
                    CoreConfigLoader.loadWithStatus(plugin, fileName, removedKeys);
            if (!existed) {
                return true;
            }
            return result.changed();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not auto-update " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    /** One managed config file and the keys the plugin no longer supports. */
    public record ConfigSpec(String path, List<String> removedKeys) {
        public ConfigSpec {
            removedKeys = List.copyOf(removedKeys);
        }
    }
}