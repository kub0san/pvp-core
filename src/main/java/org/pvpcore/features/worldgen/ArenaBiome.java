package org.pvpcore.features.worldgen;

import org.bukkit.block.Biome;

/** One generator id and one surface profile per PvP arena biome. */
enum ArenaBiome {

    DESERT("DESERT", Biome.DESERT, new DesertTerrain()),
    PLAINS("PLAINS", Biome.PLAINS, new PlainsTerrain());

    private final String id;
    private final Biome biome;
    private final TerrainProfile terrain;

    ArenaBiome(String id, Biome biome, TerrainProfile terrain) {
        this.id = id;
        this.biome = biome;
        this.terrain = terrain;
    }

    String id() {
        return id;
    }

    String configPath() {
        return id.toLowerCase(java.util.Locale.ROOT);
    }

    Biome biome() {
        return biome;
    }

    TerrainProfile terrain() {
        return terrain;
    }

    /** Case-insensitive lookup; unknown or missing ids return {@code null}. */
    static ArenaBiome byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        // Some world managers retain the full "PvPCore:DESERT" generator string.
        String bare = id;
        int colon = bare.indexOf(':');
        if (colon >= 0) {
            bare = bare.substring(colon + 1);
        }
        if (bare.isEmpty()) {
            return null;
        }
        for (ArenaBiome biome : values()) {
            if (biome.id.equalsIgnoreCase(bare)) {
                return biome;
            }
        }
        return null;
    }
}
