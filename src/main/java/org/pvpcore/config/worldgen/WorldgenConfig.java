package org.pvpcore.config.worldgen;

import org.bukkit.configuration.file.FileConfiguration;
import org.pvpcore.config.CoreConfigLoader;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldgenConfig {
    private WorldgenConfig() {
    }

    public static FileConfiguration load(JavaPlugin plugin) {
        return CoreConfigLoader.load(plugin, "worldgen/config.yml");
    }
}