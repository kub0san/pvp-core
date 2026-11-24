package org.pvpcore.gui.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.features.teleport.rtp.RtpRegion;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Presentation-only RTP destination settings.
 *
 * <p>Keeps GUI names, icons and descriptions separate from the actual RTP region
 * configuration, so changing the appearance never changes a world's teleport/search
 * settings.</p>
 *
 * <p>One shared instance exists ({@link #shared()}); it is re-read from disk only
 * when the file changed since the last read, so a screen opening does not re-parse
 * an untouched config. In-game edits go through this instance and update the
 * marker themselves, and manual edits to {@code rtp-gui.yml} are picked up on the
 * next screen open. (Note: {@code File.lastModified()} granularity is about one
 * second on some filesystems, so a same-second edit can be missed for one open.)
 * </p>
 */
final class RtpAppearance {

    private static volatile RtpAppearance shared;

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;
    private long lastModified;

    private RtpAppearance() {
        this.plugin = JavaPlugin.getProvidingPlugin(RtpAppearance.class);
        this.file = new File(plugin.getDataFolder(), "rtp-gui.yml");
        reload();
    }

    /** The shared presentation store. */
    static RtpAppearance shared() {
        RtpAppearance instance = shared;
        if (instance == null) {
            synchronized (RtpAppearance.class) {
                instance = shared;
                if (instance == null) {
                    instance = new RtpAppearance();
                    shared = instance;
                }
            }
        }
        instance.reloadIfChanged();
        return instance;
    }

    /** Re-reads rtp-gui.yml when the file changed since the last read. */
    synchronized void reloadIfChanged() {
        long modified = file.lastModified();
        if (modified != lastModified) {
            reload();
        }
    }

    synchronized void reload() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create the plugin data folder for rtp-gui.yml.");
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        this.lastModified = file.lastModified();
    }

    String displayName(RtpRegion region) {
        String configured = getString(region, "display-name", "");
        return configured.isBlank() ? prettify(region.id()) : configured;
    }

    String description(RtpRegion region) {
        return getString(region, "description", "A destination for random teleportation.");
    }

    Material icon(RtpRegion region) {
        String configured = getString(region, "icon", "");
        if (!configured.isBlank()) {
            Material material = Material.matchMaterial(configured);
            if (material != null && material.isItem() && !material.isAir()) {
                return material;
            }
        }
        return defaultIcon(region.worldName());
    }

    synchronized void setDisplayName(String regionId, String value) {
        set(regionId, "display-name", value);
    }

    synchronized void setDescription(String regionId, String value) {
        set(regionId, "description", value);
    }

    synchronized void setIcon(String regionId, Material material) {
        if (material == null || material.isAir() || !material.isItem()) {
            return;
        }
        set(regionId, "icon", material.name());
    }

    synchronized void remove(String regionId) {
        config.set(path(regionId), null);
        save();
    }

    private synchronized String getString(RtpRegion region, String key, String fallback) {
        return config.getString(path(region.id()) + "." + key, fallback);
    }

    private void set(String regionId, String key, String value) {
        config.set(path(regionId) + "." + key, value);
        save();
    }

    private void save() {
        try {
            config.save(file);
            // Our own write counts as "seen"; the mtime check must not re-read
            // the file the GUI just wrote.
            this.lastModified = file.lastModified();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save rtp-gui.yml: " + exception.getMessage());
        }
    }

    private static String path(String regionId) {
        // Region ids created by the GUI are already normalized; sanitize legacy ids as well.
        String key = regionId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return "destinations." + key;
    }

    static String prettify(String value) {
        String cleaned = value.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) {
            return "RTP Destination";
        }
        StringBuilder result = new StringBuilder(cleaned.length());
        boolean capitalize = true;
        for (char character : cleaned.toCharArray()) {
            if (capitalize && Character.isLetter(character)) {
                result.append(Character.toUpperCase(character));
                capitalize = false;
            } else {
                result.append(character);
            }
            if (character == ' ') {
                capitalize = true;
            }
        }
        return result.toString();
    }

    /**
     * The default icon for a world: environment-aware when the world is loaded,
     * name heuristics when it is not (e.g. a saved destination whose world never
     * loaded).
     */
    static Material defaultIcon(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return switch (world.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> nameHeuristicIcon(worldName);
            };
        }
        return nameHeuristicIcon(worldName);
    }

    private static Material nameHeuristicIcon(String worldName) {
        String lower = worldName.toLowerCase(Locale.ROOT);
        if (lower.contains("nether")) return Material.NETHERRACK;
        if (lower.equals("world_the_end") || lower.endsWith("_end") || lower.contains("the_end")) {
            return Material.END_STONE;
        }
        if (lower.contains("desert")) return Material.SAND;
        return Material.GRASS_BLOCK;
    }
}
