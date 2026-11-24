package org.pvpcore.config.core;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal config wrapper. Every mechanic works without configuration; the config remembers
 * whether a mechanic is currently enabled and any mechanic-specific options.
 */
public final class CoreConfig {

    private static final String MECHANICS = "mechanics";
    private static final String DEBUG = "debug";

    private final JavaPlugin plugin;

    public CoreConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    /**
     * Reads the root {@code debug} flag. Mechanics use it for verbose, per-event logging.
     * Reads the live config, so {@code /pvp-core reload} applies changes.
     */
    public static boolean isDebug(JavaPlugin plugin) {
        return plugin.getConfig().getBoolean(DEBUG, false);
    }

    /** Reads {@code mechanics.<id>.enabled}, falling back to the mechanic's default. */
    public boolean isEnabled(String id, boolean fallback) {
        return config().getBoolean(key(id), fallback);
    }

    /** Persists {@code mechanics.<id>.enabled}. */
    public void setEnabled(String id, boolean enabled) {
        FileConfiguration config = config();
        config.set(key(id), enabled);
        plugin.saveConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    private static String key(String id) {
        return MECHANICS + "." + id + ".enabled";
    }
}
