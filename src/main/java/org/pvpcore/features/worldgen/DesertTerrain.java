package org.pvpcore.features.worldgen;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/** Desert surface: sand over sandstone, with cacti and dead bushes. Uses TerrainShape for height. */
final class DesertTerrain implements TerrainProfile {

    private static final int    SAND_LAYERS       = 2;
    private static final int    SANDSTONE_LAYERS  = 2;
    private static final double SURFACE_BLEND     = 0.20;
    private static final double CACTUS_CHANCE     = 0.25;
    private static final double DEAD_BUSH_CHANCE  = 0.25;

    @Override
    public int surfaceY(WorldGenSettings.Terrain cfg, long seed, int x, int z, int minY, int maxY) {
        return TerrainShape.surfaceY(cfg.surfaceY(), cfg.tierHeight(), cfg.slopeSmoothness(),
                                     cfg.roughness(), seed, x, z, minY, maxY);
    }

    @Override
    public void generateSurface(ChunkData data, int lx, int lz, int surface, long seed,
                                int worldX, int worldZ, WorldGenSettings worldSettings,
                                int minY, int maxY) {
        for (int depth = 0; depth < SAND_LAYERS; depth++) {
            int y = surface - depth;
            double ssc = SAND_LAYERS <= 1 ? 0.0 : SURFACE_BLEND * depth / (SAND_LAYERS - 1.0);
            data.setBlock(lx, y, lz,
                    NoiseHelper.positionHash01(worldX, y, worldZ, seed) < ssc
                            ? Material.SANDSTONE : Material.SAND);
        }
        for (int depth = 0; depth < SANDSTONE_LAYERS; depth++) {
            int y = surface - SAND_LAYERS - depth;
            double sc = SANDSTONE_LAYERS <= 1 ? 0.0
                    : SURFACE_BLEND * (1.0 - depth / (SANDSTONE_LAYERS - 1.0));
            data.setBlock(lx, y, lz,
                    NoiseHelper.positionHash01(worldX, y, worldZ, seed) < sc
                            ? Material.SAND : Material.SANDSTONE);
        }
    }

    @Override
    public void decorate(WorldGenSettings settings, WorldInfo info, Random random,
                         int chunkX, int chunkZ, LimitedRegion region) {
        int baseX = chunkX << 4, baseZ = chunkZ << 4;
        if (random.nextDouble() < CACTUS_CHANCE)    placeCactus  (settings, info, region, random, baseX, baseZ);
        if (random.nextDouble() < DEAD_BUSH_CHANCE) placeDeadBush(settings, info, region, random, baseX, baseZ);
    }

    private void placeCactus(WorldGenSettings settings, WorldInfo info, LimitedRegion region,
                             Random random, int baseX, int baseZ) {
        int x = baseX + random.nextInt(16), z = baseZ + random.nextInt(16);
        int sy = settings.surfaceAt(ArenaBiome.DESERT, info.getSeed(), x, z,
                                    info.getMinHeight(), info.getMaxHeight());
        if (region.getType(x, sy, z) != Material.SAND) return;
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
            if (region.getType(x + d[0], sy, z + d[1]) != Material.SAND) return;
        int h = 1 + random.nextInt(3);
        for (int i = 1; i <= h; i++) {
            if (!region.getType(x, sy + i, z).isAir()) break;
            region.setBlockData(x, sy + i, z, Material.CACTUS.createBlockData());
        }
    }

    private void placeDeadBush(WorldGenSettings settings, WorldInfo info, LimitedRegion region,
                               Random random, int baseX, int baseZ) {
        int x = baseX + random.nextInt(16), z = baseZ + random.nextInt(16);
        int sy = settings.surfaceAt(ArenaBiome.DESERT, info.getSeed(), x, z,
                                    info.getMinHeight(), info.getMaxHeight());
        if (region.getType(x, sy, z) == Material.SAND && region.getType(x, sy + 1, z).isAir())
            region.setBlockData(x, sy + 1, z, Material.DEAD_BUSH.createBlockData());
    }
}
