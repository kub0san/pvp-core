package org.pvpcore.features.worldgen;

import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Runs {@link VanillaOres} as a populator. A populator (rather than ChunkData in
 * generateNoise) is what lets veins cross chunk borders the way vanilla's do: the
 * {@link LimitedRegion} covers the chunk plus a 16 block margin on every side.
 */
final class UndergroundPopulator extends BlockPopulator {

    private final WorldGenSettings settings;

    UndergroundPopulator(WorldGenSettings settings) {
        this.settings = settings;
    }

    @Override
    public void populate(@NotNull WorldInfo info, @NotNull Random random,
                         int chunkX, int chunkZ, @NotNull LimitedRegion region) {
        WorldGenSettings.Snapshot snapshot = settings.snapshot();
        if (!settings.insideRadius(snapshot, chunkX, chunkZ)) {
            return;
        }
        final int minY = info.getMinHeight();
        final int maxY = info.getMaxHeight();

        VanillaOres.generate(new VanillaOres.Blocks() {
            @Override
            public Material get(int x, int y, int z) {
                return region.isInRegion(x, y, z) ? region.getType(x, y, z) : null;
            }

            @Override
            public void set(int x, int y, int z, Material material) {
                if (region.isInRegion(x, y, z)) {
                    region.setType(x, y, z, material);
                }
            }
        }, random, chunkX, chunkZ, minY, maxY);
    }
}
