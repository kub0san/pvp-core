package org.pvpcore.features.worldgen;

import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/** Delegates optional surface decoration to the world's selected terrain profile. */
final class Decorator extends BlockPopulator {

    private final WorldGenSettings settings;
    private final ArenaBiome biome;

    Decorator(WorldGenSettings settings, ArenaBiome biome) {
        this.settings = settings;
        this.biome = biome;
    }

    @Override
    public void populate(@NotNull WorldInfo info, @NotNull Random random,
                         int chunkX, int chunkZ, @NotNull LimitedRegion region) {
        if (settings.insideRadius(chunkX, chunkZ)) {
            biome.terrain().decorate(settings, info, random, chunkX, chunkZ, region);
        }
    }
}
