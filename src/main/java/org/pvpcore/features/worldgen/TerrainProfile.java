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

    /**
     * Replaces the top of an already solid stone column, reading the current settings snapshot.
     * Convenience overload for callers that did not capture a snapshot up front.
     */
    default void generateSurface(ChunkData data, int lx, int lz, int surface, long seed,
                                 int worldX, int worldZ, WorldGenSettings worldSettings,
                                 int minY, int maxY) {
        generateSurface(data, lx, lz, surface, seed, worldX, worldZ,
                worldSettings.snapshot(), minY, maxY);
    }

    /** Replaces the top of an already solid stone column using one captured settings snapshot. */
    void generateSurface(ChunkData data, int lx, int lz, int surface, long seed,
                         int worldX, int worldZ, WorldGenSettings.Snapshot snapshot,
                         int minY, int maxY);

    /**
     * Optional surface decoration with no captured snapshot. Delegates to the snapshot-aware
     * variant using the current generation, so an unchanged caller still reads one consistent
     * set of settings.
     */
    default void decorate(WorldGenSettings settings, WorldInfo info, Random random,
                          int chunkX, int chunkZ, LimitedRegion region) {
        decorate(settings.snapshot(), info, random, chunkX, chunkZ, region);
    }

    /** Optional surface decoration, called after the shared underground populator. */
    default void decorate(WorldGenSettings.Snapshot snapshot, WorldInfo info, Random random,
                          int chunkX, int chunkZ, LimitedRegion region) {
        // Clear terrain by default.
    }
}
