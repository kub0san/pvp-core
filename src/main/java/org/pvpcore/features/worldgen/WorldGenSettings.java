package org.pvpcore.features.worldgen;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.pvpcore.PvPCorePlugin;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Immutable-per-reload worldgen settings, with independent terrain values for each biome. */
final class WorldGenSettings {

    record Terrain(int surfaceY, double tierHeight, double slopeSmoothness, double roughness) {}

    private static final Map<ArenaBiome, Terrain> DEFAULTS = Map.of(
            ArenaBiome.DESERT, new Terrain(64, 12.0, 0.14, 1.0),
            ArenaBiome.PLAINS, new Terrain(64, 32.0, 0.08, 0.6)
    );

    /**
     * One immutable generation of settings. A single volatile reference publishes all values at
     * once, so a reader that captured a snapshot can never observe a half-reloaded state.
     * The terrain map and expected-world set are immutable copies.
     */
    record Snapshot(Map<ArenaBiome, Terrain> terrain,
                    int maxRadiusBlocks,
                    int worldBorderDiameter,
                    Set<String> expectedWorlds,
                    boolean allowMobSpawning,
                    boolean allowWeatherCycle,
                    boolean allowLocatorBar,
                    boolean allowDayCycle,
                    long dayTime) {}

    private volatile Snapshot snapshot = new Snapshot(
            DEFAULTS, 5000, 10000, Set.of(), false, false, false, false, 6000L);

    /** Production entry point; delegates to the testable logger/config seam. */
    void load(PvPCorePlugin plugin, FileConfiguration config) {
        load(plugin.getLogger(), config);
    }

    /** Builds one snapshot locally and publishes it with a single volatile write. */
    void load(Logger logger, FileConfiguration config) {
        ConfigurationSection section = config;

        Map<ArenaBiome, Terrain> loadedTerrain = new EnumMap<>(ArenaBiome.class);
        for (ArenaBiome biome : ArenaBiome.values()) {
            loadedTerrain.put(biome, readTerrain(logger, section, biome));
        }

        int maxRadius = section == null ? 5000 : Math.max(0, section.getInt("max-radius-blocks", 5000));
        int borderDiameter = section == null ? 10000
                : Math.max(1, section.getInt("world-border-diameter", 10000));
        boolean mobSpawn = section != null && section.getBoolean("allow-mob-spawning", false);
        boolean weather = section != null && section.getBoolean("allow-weather-cycle", false);
        boolean locatorBar = section != null && section.getBoolean("allow-locator-bar", false);
        boolean dayCycle = false;
        long fixedDayTime = 6000L;
        if (section != null) {
            if (section.contains("allow-day-cycle")) {
                dayCycle = section.getBoolean("allow-day-cycle", false);
            } else if (section.contains("allow-daylight-cycle")) {
                dayCycle = section.getBoolean("allow-daylight-cycle", false);
            } else if (section.contains("disable-day-cycle")) {
                dayCycle = !section.getBoolean("disable-day-cycle", true);
            } else if (section.contains("disable-daylight-cycle")) {
                dayCycle = !section.getBoolean("disable-daylight-cycle", true);
            }
            if (section.contains("day-time")) {
                fixedDayTime = section.getLong("day-time", 6000L);
            } else if (section.contains("daylight-cycle-time")) {
                fixedDayTime = section.getLong("daylight-cycle-time", 6000L);
            } else if (section.contains("time")) {
                fixedDayTime = section.getLong("time", 6000L);
            } else if (section.contains("day-time-ticks")) {
                fixedDayTime = section.getLong("day-time-ticks", 6000L);
            }
        }

        if (maxRadius < 16) {
            logger.warning("worldgen: max-radius-blocks=" + maxRadius
                    + " is smaller than one chunk; using 16.");
            maxRadius = 16;
        }

        Set<String> expected = Set.of();
        if (section != null) {
            List<String> list = section.getStringList("expected-worlds");
            if (!list.isEmpty()) {
                Set<String> tmp = new HashSet<>();
                for (String world : list) {
                    if (world != null && !world.isEmpty()) {
                        tmp.add(world);
                    }
                }
                expected = Set.copyOf(tmp);
            }
        }

        this.snapshot = new Snapshot(Map.copyOf(loadedTerrain), maxRadius, borderDiameter,
                expected, mobSpawn, weather, locatorBar, dayCycle, fixedDayTime);
    }

    private static Terrain readTerrain(Logger logger, ConfigurationSection worldgen,
                                       ArenaBiome biome) {
        Terrain defaults = DEFAULTS.get(biome);
        ConfigurationSection values = worldgen == null ? null
                : worldgen.getConfigurationSection("biomes." + biome.configPath());

        // Existing desert configs predate per-biome sections. Preserve them exactly.
        boolean legacyDesert = biome == ArenaBiome.DESERT && values == null;
        if (legacyDesert) {
            values = worldgen;
        }
        String path = legacyDesert ? "worldgen/config.yml" : "worldgen.biomes." + biome.configPath();

        int surfaceY = values == null ? defaults.surfaceY()
                : values.getInt("surface-y", defaults.surfaceY());
        double tierHeight = values == null ? defaults.tierHeight()
                : values.getDouble("tier-height", defaults.tierHeight());
        double smoothness = values == null ? defaults.slopeSmoothness()
                : values.getDouble("slope-smoothness", defaults.slopeSmoothness());
        double roughness = values == null ? defaults.roughness()
                : values.getDouble("roughness", defaults.roughness());

        if (surfaceY < 40) {
            logger.warning(path + ": surface-y=" + surfaceY + " is below 40; using 40.");
            surfaceY = 40;
        } else if (surfaceY > 200) {
            logger.warning(path + ": surface-y=" + surfaceY + " is above 200; using 200.");
            surfaceY = 200;
        }
        if (!Double.isFinite(tierHeight) || tierHeight < 0 || tierHeight > 40) {
            logger.warning(path + ": tier-height=" + tierHeight
                    + " is outside 0..40; using " + defaults.tierHeight() + ".");
            tierHeight = defaults.tierHeight();
        }
        // Plains deliberately supports narrower blend bands for steeper PvP terrain. Keep the
        // desert's original lower bound unchanged so existing desert settings behave as before.
        double minSmoothness = biome == ArenaBiome.PLAINS ? 0.01 : 0.03;
        if (!Double.isFinite(smoothness) || smoothness < minSmoothness || smoothness > 0.5) {
            logger.warning(path + ": slope-smoothness=" + smoothness
                    + " is outside " + minSmoothness + "..0.5; using "
                    + defaults.slopeSmoothness() + ".");
            smoothness = defaults.slopeSmoothness();
        }
        if (!Double.isFinite(roughness) || roughness < 0 || roughness > 3) {
            logger.warning(path + ": roughness=" + roughness
                    + " is outside 0..3; using " + defaults.roughness() + ".");
            roughness = defaults.roughness();
        }
        return new Terrain(surfaceY, tierHeight, smoothness, roughness);
    }

    /** The current published generation, read with a single volatile access. */
    Snapshot snapshot() {
        return snapshot;
    }

    int maxRadiusBlocks() {
        return snapshot.maxRadiusBlocks();
    }

    int worldBorderDiameter() {
        return snapshot.worldBorderDiameter();
    }

    boolean allowMobSpawning() {
        return snapshot.allowMobSpawning();
    }

    boolean allowWeatherCycle() {
        return snapshot.allowWeatherCycle();
    }

    boolean allowLocatorBar() {
        return snapshot.allowLocatorBar();
    }

    boolean allowDayCycle() {
        return snapshot.allowDayCycle();
    }

    long dayTime() {
        return snapshot.dayTime();
    }

    /** Height of the top surface block, shared by the generator and its decorator. */
    int surfaceAt(ArenaBiome biome, long seed, int x, int z, int minY, int maxY) {
        return surfaceAt(snapshot(), biome, seed, x, z, minY, maxY);
    }

    /** Snapshot-aware variant: uses the captured generation, not the current one. */
    static int surfaceAt(Snapshot snapshot, ArenaBiome biome, long seed,
                         int x, int z, int minY, int maxY) {
        ArenaBiome effective = (biome == null) ? ArenaBiome.PLAINS : biome;
        Terrain cfg = snapshot.terrain().getOrDefault(effective,
                DEFAULTS.getOrDefault(effective, DEFAULTS.get(ArenaBiome.PLAINS)));
        return effective.terrain().surfaceY(cfg, seed, x, z, minY, maxY);
    }

    /** The current terrain settings for one biome. */
    Terrain terrainFor(ArenaBiome biome) {
        return snapshot.terrain().get(biome);
    }

    /** True if any part of the chunk is inside the generated square. */
    boolean insideRadius(int chunkX, int chunkZ) {
        return insideRadius(snapshot(), chunkX, chunkZ);
    }

    /** Snapshot-aware variant: uses the captured generation's radius. */
    boolean insideRadius(Snapshot snapshot, int chunkX, int chunkZ) {
        int radius = snapshot.maxRadiusBlocks();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        return !(maxX < -radius || minX > radius || maxZ < -radius || minZ > radius);
    }

    // Phase 2 accessors (stubs; not yet read from config).
    int pregenChunksPerTick()         { return 2; }
    long autoResetIntervalHours()     { return 24L; }
    int autoResetDestroyedThreshold() { return 5000; }
}
