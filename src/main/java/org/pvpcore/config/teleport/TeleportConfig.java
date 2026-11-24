package org.pvpcore.config.teleport;

import org.bukkit.configuration.file.FileConfiguration;
import org.pvpcore.config.CoreConfigLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class TeleportConfig {
    private TeleportConfig() {
    }

    public static FileConfiguration loadConfig(JavaPlugin plugin, File folder) {
        File file = new File(folder, "config.yml");
        String relative = plugin.getDataFolder().toPath().relativize(file.toPath()).toString().replace('\\', '/');
        return CoreConfigLoader.load(plugin, relative);
    }

    public static FileConfiguration loadRtp(JavaPlugin plugin, File folder) {
        File file = new File(folder, "rtp.yml");
        String relative = plugin.getDataFolder().toPath().relativize(file.toPath()).toString().replace('\\', '/');
        return CoreConfigLoader.load(plugin, relative);
    }
}