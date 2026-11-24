package org.pvpcore.features.teleport.rtp;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Set;

/**
 * Decides whether a column is a safe place to land. Reads blocks, so it must run on the main
 * thread and only on a loaded chunk. Stateless apart from its configuration.
 */
final class RtpValidator {

    /** Returned by {@link #findFeetY} when the column is not usable. */
    static final int INVALID = Integer.MIN_VALUE;

    private final Set<Material> unsafe;
    private final Set<String> blockedBiomes;
    private final List<LocationVerifier> verifiers;

    RtpValidator(Set<Material> unsafe, Set<String> blockedBiomes, List<LocationVerifier> verifiers) {
        this.unsafe = unsafe;
        this.blockedBiomes = blockedBiomes;
        this.verifiers = List.copyOf(verifiers);
    }

    /** The Y of the player's feet for column (x, z), or {@link #INVALID}. */
    int findFeetY(World world, int x, int z, RtpRegion region) {
        int low = Math.max(region.minY(), world.getMinHeight() + 1);
        int high = Math.min(region.maxY(), world.getMaxHeight() - 3);
        if (low > high) {
            return INVALID;
        }

        int feet = INVALID;
        if (region.vertical() == RtpRegion.Vertical.SURFACE) {
            int candidate = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            if (candidate >= low && candidate <= high && isStandable(world, x, candidate, z)) {
                feet = candidate;
            }
        } else {
            for (int y = high; y >= low; y--) {
                if (isStandable(world, x, y, z)) {
                    feet = y;
                    break;
                }
            }
        }
        if (feet == INVALID || !passesExtraChecks(world, x, feet, z)) {
            return INVALID;
        }
        return feet;
    }

    /**
     * Solid, safe ground under the feet and two free blocks for the body. Also used to
     * re-check a pooled spot just before it is handed out.
     */
    boolean isStandable(World world, int x, int feet, int z) {
        Material ground = world.getBlockAt(x, feet - 1, z).getType();
        if (!ground.isSolid() || unsafe.contains(ground) || Tag.LEAVES.isTagged(ground)) {
            return false;
        }
        return isClear(world.getBlockAt(x, feet, z)) && isClear(world.getBlockAt(x, feet + 1, z));
    }

    private boolean isClear(Block block) {
        return block.isPassable() && !block.isLiquid() && !unsafe.contains(block.getType());
    }

    private boolean passesExtraChecks(World world, int x, int feet, int z) {
        if (!blockedBiomes.isEmpty()) {
            Biome biome = world.getBiome(x, feet, z);
            if (blockedBiomes.contains(biome.getKey().toString())) {
                return false;
            }
        }
        if (!verifiers.isEmpty()) {
            Location location = new Location(world, x + 0.5, feet, z + 0.5);
            for (LocationVerifier verifier : verifiers) {
                if (!verifier.allows(location)) {
                    return false;
                }
            }
        }
        return true;
    }
}
