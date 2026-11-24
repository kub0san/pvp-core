package org.pvpcore.features.worldgen;

import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Biome-specific surface behavior. The stone column, bedrock, deepslate, ores, and underground
 * pockets are shared; implementations only choose the height, surface materials, and decoration.
 */
interface TerrainProfile {

    int surfaceY(WorldGenSettings.Terrain settings, long seed, int x, int z, int minY, int maxY);

    /** Replaces the top of an already solid stone column. */
    void generateSurface(ChunkData data, int lx, int lz, int surface, long seed,
                         int worldX, int worldZ, WorldGenSettings worldSettings,
                         int minY, int maxY);

    /** Optional surface decoration, called after the shared underground populator. */
    default void decorate(WorldGenSettings settings, WorldInfo info, Random random,
                          int chunkX, int chunkZ, LimitedRegion region) {
        // Clear terrain by default.
    }
}
