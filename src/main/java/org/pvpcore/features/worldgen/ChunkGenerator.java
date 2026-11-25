package org.pvpcore.features.worldgen;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * Shared PvP world pipeline: biome-specific surface over common stone, bedrock, deepslate,
 * and underground features. Caves, water, structures, and vanilla decoration are disabled.
 */
final class ChunkGenerator extends org.bukkit.generator.ChunkGenerator {

    private static final int[] BEDROCK_GRADIENT_NUMERATOR = {0, 4, 3, 2, 1};
    private static final int BEDROCK_GRADIENT_DENOMINATOR = 5;
    private static final int DEEPSLATE_FULL_Y = 0;
    private static final int DEEPSLATE_TRANSITION_TOP = 8;

    private final WorldGenSettings settings;
    private final ArenaBiome biome;
    private final BiomeProvider biomeProvider;

    ChunkGenerator(WorldGenSettings settings, ArenaBiome biome) {
        this.settings = settings;
        this.biome = biome;
        this.biomeProvider = new BiomeProvider() {
            @Override
            public @NotNull Biome getBiome(@NotNull WorldInfo info, int x, int y, int z) {
                return biome.biome();
            }

            @Override
            public @NotNull List<Biome> getBiomes(@NotNull WorldInfo info) {
                return List.of(biome.biome());
            }
        };
    }

    @Override public boolean shouldGenerateNoise()       { return false; }
    @Override public boolean shouldGenerateSurface()     { return false; }
    @Override public boolean shouldGenerateCaves()       { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs()        { return false; }
    @Override public boolean shouldGenerateStructures()  { return false; }

    @Override
    public void generateNoise(@NotNull WorldInfo info, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData data) {
        WorldGenSettings.Snapshot snapshot = settings.snapshot();
        if (!settings.insideRadius(snapshot, chunkX, chunkZ)) {
            return;
        }

        int minY = info.getMinHeight();
        int maxY = info.getMaxHeight();
        long seed = info.getSeed();

        generateTerrainColumns(data, chunkX, chunkZ, seed, minY, maxY, snapshot);
        applyBedrock(data, chunkX, chunkZ, seed, minY, maxY);
        applyDeepslate(data, chunkX, chunkZ, seed, minY, maxY);
    }

    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return List.of(new UndergroundPopulator(settings), new Decorator(settings, biome));
    }

    @Override
    public @NotNull BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return biomeProvider;
    }

    private void generateTerrainColumns(ChunkData data, int chunkX, int chunkZ,
                                        long seed, int minY, int maxY,
                                        WorldGenSettings.Snapshot snapshot) {
        for (int lx = 0; lx < 16; lx++) {
            int worldX = (chunkX << 4) + lx;
            for (int lz = 0; lz < 16; lz++) {
                int worldZ = (chunkZ << 4) + lz;
                int surface = WorldGenSettings.surfaceAt(snapshot, biome, seed,
                        worldX, worldZ, minY, maxY);

                // Every biome starts with the same solid stone column. Its profile replaces
                // only the surface layers, so underground generation cannot diverge by biome.
                data.setRegion(lx, minY + 2, lz, lx + 1, surface + 1, lz + 1, Material.STONE);
                biome.terrain().generateSurface(data, lx, lz, surface, seed, worldX, worldZ,
                        snapshot, minY, maxY);
            }
        }
    }

    /** Vanilla-distribution bedrock floor, with deterministic positions across chunk borders. */
    private static void applyBedrock(ChunkData data, int chunkX, int chunkZ,
                                     long seed, int minY, int maxY) {
        data.setRegion(0, minY, 0, 16, minY + 1, 16, Material.BEDROCK);
        for (int lx = 0; lx < 16; lx++) {
            int worldX = (chunkX << 4) + lx;
            for (int lz = 0; lz < 16; lz++) {
                int worldZ = (chunkZ << 4) + lz;
                for (int k = 1; k <= 4; k++) {
                    int y = minY + k;
                    if (y >= maxY) continue;
                    double chance = BEDROCK_GRADIENT_NUMERATOR[k]
                            / (double) BEDROCK_GRADIENT_DENOMINATOR;
                    if (NoiseHelper.positionHash01(worldX, y, worldZ, seed) < chance) {
                        data.setBlock(lx, y, lz, Material.BEDROCK);
                    } else {
                        data.setBlock(lx, y, lz, y <= 0 ? Material.DEEPSLATE : Material.STONE);
                    }
                }
            }
        }
    }

    /** Vanilla's 0..8 vertical gradient, sampled with a deterministic positional hash. */
    private static void applyDeepslate(ChunkData data, int chunkX, int chunkZ,
                                       long seed, int minY, int maxY) {
        int topY = Math.min(DEEPSLATE_TRANSITION_TOP - 1, maxY - 1);
        for (int lx = 0; lx < 16; lx++) {
            int worldX = (chunkX << 4) + lx;
            for (int lz = 0; lz < 16; lz++) {
                int worldZ = (chunkZ << 4) + lz;
                for (int y = minY + 2; y <= topY; y++) {
                    if (data.getType(lx, y, lz) != Material.STONE) continue;
                    if (y <= DEEPSLATE_FULL_Y) {
                        data.setBlock(lx, y, lz, Material.DEEPSLATE);
                    } else {
                        double chance = 1.0 - (double) y / DEEPSLATE_TRANSITION_TOP;
                        if (chance > 0.0 && NoiseHelper.positionHash01(worldX, y, worldZ, seed) < chance) {
                            data.setBlock(lx, y, lz, Material.DEEPSLATE);
                        }
                    }
                }
            }
        }
    }

}
