package org.pvpcore.storage.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * storage.yml. Holds the single server-wide spawn point. Written only on /setspawn, so a
 * plain YAML file with an atomic replace is enough.
 */
public final class TeleportStorage {

    private static final String SPAWN = "spawn";

    private final File file;
    private final Logger logger;
    private YamlConfiguration yaml = new YamlConfiguration();

    public TeleportStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    /** The spawn point, or empty if none is set or its world is not loaded. */
    public Optional<Location> spawn() {
        String world = yaml.getString(SPAWN + ".world");
        if (world == null) {
            return Optional.empty();
        }
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(bukkitWorld,
                yaml.getDouble(SPAWN + ".x"),
                yaml.getDouble(SPAWN + ".y"),
                yaml.getDouble(SPAWN + ".z"),
                (float) yaml.getDouble(SPAWN + ".yaw"),
                (float) yaml.getDouble(SPAWN + ".pitch")));
    }

    public void setSpawn(Location location) {
        yaml.set(SPAWN + ".world", location.getWorld().getName());
        yaml.set(SPAWN + ".x", location.getX());
        yaml.set(SPAWN + ".y", location.getY());
        yaml.set(SPAWN + ".z", location.getZ());
        yaml.set(SPAWN + ".yaw", (double) location.getYaw());
        yaml.set(SPAWN + ".pitch", (double) location.getPitch());
        save();
    }

    /** Writes to a temp file, then moves it over storage.yml so a crash cannot leave it half-written. */
    private void save() {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            yaml.save(tmp);
            Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.severe("teleportation: could not save storage.yml: " + e);
        }
    }
}