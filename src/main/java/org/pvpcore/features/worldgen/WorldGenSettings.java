package org.pvpcore.features.worldgen;

import org.bukkit.configuration.ConfigurationSection;
import org.pvpcore.PvPCorePlugin;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable-per-reload worldgen settings, with independent terrain values for each biome. */
final class WorldGenSettings {

    record Terrain(int surfaceY, double tierHeight, double slopeSmoothness, double roughness) {}

    private static final Map<ArenaBiome, Terrain> DEFAULTS = Map.of(
            ArenaBiome.DESERT, new Terrain(64, 12.0, 0.14, 1.0),
            ArenaBiome.PLAINS, new Terrain(64, 32.0, 0.08, 0.6)
    );

    private volatile Map<ArenaBiome, Terrain> terrain = DEFAULTS;
    private volatile int maxRadiusBlocks = 5000;
    private volatile int worldBorderDiameter = 10000;
    private volatile boolean allowMobSpawning = false;
    private volatile boolean allowWeatherCycle = false;
    private volatile boolean allowLocatorBar = false;
    private volatile boolean allowDayCycle = false;
    private volatile long dayTime = 6000L;
    private volatile Set<String> expectedWorlds = Set.of();

    void load(PvPCorePlugin plugin, org.bukkit.configuration.file.FileConfiguration config) {
        ConfigurationSection section = config;

        Map<ArenaBiome, Terrain> loadedTerrain = new EnumMap<>(ArenaBiome.class);
        for (ArenaBiome biome : ArenaBiome.values()) {
            loadedTerrain.put(biome, readTerrain(plugin, section, biome));
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
            plugin.getLogger().warning("worldgen: max-radius-blocks=" + maxRadius
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

        this.terrain = Map.copyOf(loadedTerrain);
        this.maxRadiusBlocks = maxRadius;
        this.worldBorderDiameter = borderDiameter;
        this.allowMobSpawning = mobSpawn;
        this.allowWeatherCycle = weather;
        this.allowLocatorBar = locatorBar;
        this.allowDayCycle = dayCycle;
        this.dayTime = fixedDayTime;
        this.expectedWorlds = expected;
    }

    private static Terrain readTerrain(PvPCorePlugin plugin, ConfigurationSection worldgen,
                                       ArenaBiome biome) {
        Terrain defaults = DEFAULTS.get(biome);
        ConfigurationSection values = worldgen == null ? null
                : worldgen.getConfigurationSection("biomes." + biome.configPath());

        // Existing desert configs predate per-biome sections. Preserve them exactly.
        boolean legacyDesert = biome == ArenaBiome.DESERT && values == null;
        if (legacyDesert) {
            values = worldgen;
        }
        String path = legacyDesert ? "worldgen" : "worldgen.biomes." + biome.configPath();

        int surfaceY = values == null ? defaults.surfaceY()
                : values.getInt("surface-y", defaults.surfaceY());
        double tierHeight = values == null ? defaults.tierHeight()
                : values.getDouble("tier-height", defaults.tierHeight());
        double smoothness = values == null ? defaults.slopeSmoothness()
                : values.getDouble("slope-smoothness", defaults.slopeSmoothness());
        double roughness = values == null ? defaults.roughness()
                : values.getDouble("roughness", defaults.roughness());

        if (surfaceY < 40) {
            plugin.getLogger().warning(path + ": surface-y=" + surfaceY + " is below 40; using 40.");
            surfaceY = 40;
        } else if (surfaceY > 200) {
            plugin.getLogger().warning(path + ": surface-y=" + surfaceY + " is above 200; using 200.");
            surfaceY = 200;
        }
        if (!Double.isFinite(tierHeight) || tierHeight < 0 || tierHeight > 40) {
            plugin.getLogger().warning(path + ": tier-height=" + tierHeight
                    + " is outside 0..40; using " + defaults.tierHeight() + ".");
            tierHeight = defaults.tierHeight();
        }
        // Plains deliberately supports narrower blend bands for steeper PvP terrain. Keep the
        // desert's original lower bound unchanged so existing desert settings behave as before.
        double minSmoothness = biome == ArenaBiome.PLAINS ? 0.01 : 0.03;
        if (!Double.isFinite(smoothness) || smoothness < minSmoothness || smoothness > 0.5) {
            plugin.getLogger().warning(path + ": slope-smoothness=" + smoothness
                    + " is outside " + minSmoothness + "..0.5; using "
                    + defaults.slopeSmoothness() + ".");
            smoothness = defaults.slopeSmoothness();
        }
        if (!Double.isFinite(roughness) || roughness < 0 || roughness > 3) {
            plugin.getLogger().warning(path + ": roughness=" + roughness
                    + " is outside 0..3; using " + defaults.roughness() + ".");
            roughness = defaults.roughness();
        }
        return new Terrain(surfaceY, tierHeight, smoothness, roughness);
    }

    int maxRadiusBlocks() {
        return maxRadiusBlocks;
    }

    int worldBorderDiameter() {
        return worldBorderDiameter;
    }

    boolean allowMobSpawning() {
        return allowMobSpawning;
    }

    boolean allowWeatherCycle() {
        return allowWeatherCycle;
    }

    boolean allowLocatorBar() {
        return allowLocatorBar;
    }

    boolean allowDayCycle() {
        return allowDayCycle;
    }

    long dayTime() {
        return dayTime;
    }

    /** Height of the top surface block, shared by the generator and its decorator. */
    int surfaceAt(ArenaBiome biome, long seed, int x, int z, int minY, int maxY) {
        Map<ArenaBiome, Terrain> snapshot = terrain;
        return biome.terrain().surfaceY(snapshot.get(biome), seed, x, z, minY, maxY);
    }

    /** The current terrain settings for one biome. */
    Terrain terrainFor(ArenaBiome biome) {
        return terrain.get(biome);
    }

    /** True if any part of the chunk is inside the generated square. */
    boolean insideRadius(int chunkX, int chunkZ) {
        int radius = maxRadiusBlocks;
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
