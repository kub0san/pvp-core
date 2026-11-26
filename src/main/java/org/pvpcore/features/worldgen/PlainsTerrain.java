package org.pvpcore.features.worldgen;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.data.Bisected;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * PvP-adapted plains terrain, modelled on how vanilla actually builds plains.
 *
 * <h3>What vanilla does (and what this copies)</h3>
 * <ul>
 *   <li><b>Shape.</b> Vanilla terrain height is driven by a few very low-frequency 2D
 *       noises (continentalness, erosion, peaks-and-valleys). Where erosion is high the
 *       land is flat, where it drops the land rises, so plains are made of a handful of
 *       huge connected landforms, not many small hills. Here one very-low-frequency field
 *       ({@link #LAND_FREQ}, ~700-block features) is cut at three thresholds into a basin,
 *       a shelf and a highland, over a dominant flat floor.</li>
 *   <li><b>Edges.</b> Each region picks its own edge style, from a near-vertical cliff
 *       through a steep staircase to a gentle ramp, so you get a chance of a cut-off
 *       cliff or of bumpy terrain. The slope is set directly: a ramp's width follows from
 *       its height and the style's target steepness.</li>
 *   <li><b>Soil.</b> Handled by {@link SurfaceMaterials#applyVanilla}: grass on every
 *       column's top, then a noise-driven 0-8 dirt blocks, then stone. Nothing is
 *       sprinkled on faces; stone shows only where soil is thin or a drop is taller than
 *       the soil, exactly as in vanilla.</li>
 * </ul>
 *
 * <h3>Config meaning for this biome</h3>
 * <ul>
 *   <li>{@code tier-height} - height of the highland above the floor (shelf = 32%,
 *       basin depth = 28% of it).</li>
 *   <li>{@code slope-smoothness} - lower = steeper edges. 0.08 gives a staircase of about
 *       1.5 blocks per block as the middle style, cliffs ~3x steeper, ramps ~3x gentler.</li>
 *   <li>{@code roughness} - floor undulation and ragged ledges on the slopes.</li>
 * </ul>
 *
 * <h3>Decoration</h3>
 * Random per-chunk oaks (1/20 chunks), rare single-or-paired pumpkins (1/300 chunks),
 * and circular grass (6 per chunk) and flower (1/14 chunks) patches whose density fades
 * toward the edges.  Flower patches blend a dominant type with a noise-mixed secondary
 * instead of placing one strict species across the whole patch.
 */
final class PlainsTerrain implements TerrainProfile {

    // ---- Landforms: the knobs you are most likely to touch ----
    /** Feature size: 0.0055 for basins (keeps them at 4-6 chunks). */
    private static final double LAND_FREQ   = 0.0055;
    /** High-frequency peak noise for hills; 0.012 creates very small, localized peaks (~3-4 chunks). */
    private static final double PEAK_FREQ   = 0.012;
    /** Where the landform field is cut. T_SHELF and T_BASIN spread out to make the flat floor (mid) ~50% dominant. */
    private static final double T_SHELF     = 0.35;
    private static final double T_HIGH      = 0.65;
    private static final double T_BASIN     = -0.35;
    /** Heights as a fraction of tier-height. */
    private static final double SHELF_FRAC  = 0.32;
    private static final double BASIN_FRAC  = 0.28;
    /** Typical |d(field)/d(block)|, which scales with LAND_FREQ. Used to turn a target slope into a ramp width. */
    private static final double GRAD_REF    = 0.86 * LAND_FREQ;
    private static final double MIN_RAMP    = 0.003;

    /** Large, intense wobble/warping of the whole field so terrain twists and tears dramatically. */
    private static final double WARP_FREQ   = 0.0065;
    private static final double WARP_STR    = 95.0;
    /** Ragged outlines: small detail added to the field for sharp cut-offs and cliffs. */
    private static final double EDGE_FREQ   = 0.015;
    private static final double EDGE_AMP    = 0.065;
    /** Region noise that picks cliff / staircase / ramp. */
    private static final double STYLE_FREQ  = 0.0040;
    /** Heavy ledge and bump noise on slopes, dialed back for cleaner, smoother slopes. */
    private static final double LEDGE_FREQ  = 0.045;
    private static final double LEDGE_FRAC  = 0.12;   // x tier-height x roughness, moderate on slopes
    private static final double TILT_FRAC   = 0.12;   // x tier-height x field

    // ---- Floor texture ----
    private static final double MICRO_FREQ  = 0.035;
    private static final double MICRO_AMP   = 0.8;
    private static final double FOOT_FREQ   = 0.009;
    private static final double FOOT_AMP    = 4.0;    // at roughness == 1

    // ---- Decoration ----
    private static final double OAK_CHANCE_PER_CHUNK          = 1.0 / 20.0;
    private static final double BIG_OAK_CHANCE                = 1.0 / 10.0;

    private static final int    GRASS_PATCHES_PER_CHUNK       = 3;
    private static final int    GRASS_ATTEMPTS_PER_PATCH      = 16;
    private static final double GRASS_RADIUS                  = 4.0;
    private static final double GRASS_CENTRE_CHANCE           = 0.45;
    private static final double GRASS_EDGE_CHANCE             = 0.05;

    private static final double FLOWER_PATCH_CHANCE_PER_CHUNK = 1.0 / 22.0;
    private static final int    FLOWER_ATTEMPTS_PER_PATCH     = 32;
    private static final double FLOWER_RADIUS                 = 3.5;
    private static final double FLOWER_CENTRE_CHANCE          = 0.55;
    private static final double FLOWER_EDGE_CHANCE            = 0.05;
    private static final double FLOWER_MIX_THRESHOLD          = 0.10;

    private static final double PUMPKIN_PATCH_CHANCE_PER_CHUNK = 1.0 / 300.0;
    private static final int    PUMPKIN_ATTEMPTS_PER_PATCH     = 16;
    private static final double PUMPKIN_RADIUS                 = 2.5;
    private static final double PUMPKIN_CENTRE_CHANCE          = 0.70;
    private static final double PUMPKIN_EDGE_CHANCE            = 0.20;
    private static final double PUMPKIN_PAIR_CHANCE            = 0.25;

    private static final double TALL_GRASS_CHANCE             = 0.10;

    private static final Material[] PLAINS_FLOWERS = {
            Material.DANDELION, Material.POPPY, Material.AZURE_BLUET,
            Material.OXEYE_DAISY, Material.CORNFLOWER,
            Material.ORANGE_TULIP, Material.RED_TULIP,
            Material.PINK_TULIP, Material.WHITE_TULIP
    };

    // =========================================================================
    // TerrainProfile: height
    // =========================================================================

    @Override
    public int surfaceY(WorldGenSettings.Terrain cfg, long seed,
                        int x, int z, int minY, int maxY) {
        return heightAt(cfg, seed, x, z, minY, maxY);
    }

    // =========================================================================
    // TerrainProfile: surface materials
    // =========================================================================

    /**
     * Vanilla's soil rule: grass on top, a noise-driven number of dirt blocks, then stone.
     * No slope probing is needed, so this is also cheaper than before.
     */
    @Override
    public void generateSurface(ChunkData data, int lx, int lz, int surface,
                                long seed, int worldX, int worldZ,
                                WorldGenSettings worldSettings, int minY, int maxY) {
        SurfaceMaterials.applyVanilla(data, lx, lz, surface, worldX, worldZ, seed);
    }

    // =========================================================================
    // TerrainProfile: decoration
    // =========================================================================

    @Override
    public void decorate(WorldGenSettings settings, WorldInfo info, Random random,
                         int chunkX, int chunkZ, LimitedRegion region) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        if (random.nextDouble() < OAK_CHANCE_PER_CHUNK) {
            placeVanillaOak(settings, info, random, region,
                    baseX + random.nextInt(16), baseZ + random.nextInt(16));
        }

        placePumpkinPatch(settings, info, random, region, baseX, baseZ);
        placeGrassPatches(settings, info, random, region, baseX, baseZ);
        placeFlowerPatch(settings, info, random, region, baseX, baseZ);
    }

    // =========================================================================
    // Internal height evaluation
    // =========================================================================

    /**
     * Height of the top block for one world column.
     *
     * <p>One field {@code F} (very low frequency, so landforms are hundreds of blocks
     * wide) decides everything. Three smooth steps of F give a basin, a shelf and a
     * highland. The width of each step is chosen from the target slope, so the edge
     * style is controlled directly rather than by accident of the noise gradient.</p>
     */
    private static int heightAt(WorldGenSettings.Terrain cfg, long seed,
                                int x, int z, int minY, int maxY) {
        double roughness = NoiseHelper.clamp(cfg.roughness(), 0.0, 3.0);
        double tierH     = NoiseHelper.clamp(cfg.tierHeight(), 4.0, 40.0);

        double wx = x + NoiseHelper.fbm(x * WARP_FREQ, z * WARP_FREQ, seed + 11L, 2) * WARP_STR;
        double wz = z + NoiseHelper.fbm(x * WARP_FREQ + 79.3, z * WARP_FREQ - 41.7, seed + 12L, 2) * WARP_STR;

        // The landform field, then ragged detail so outlines wander instead of being rounded.
        double landform = NoiseHelper.fbm(wx * LAND_FREQ, wz * LAND_FREQ, seed, 2);
        double detail = NoiseHelper.fbm(x * EDGE_FREQ, z * EDGE_FREQ, seed + 61L, 2) * EDGE_AMP
                      + NoiseHelper.fbm(x * 0.04, z * 0.04, seed + 62L, 1) * EDGE_AMP * 0.35;
        double field = landform + detail;

        // Edge style for this region: 0 = vertical cliff ... 1 = gentle ramp.
        double style = NoiseHelper.smoothstep(-0.35, 0.35, NoiseHelper.fbm(
                wx * STYLE_FREQ + 300, wz * STYLE_FREQ - 300, seed + 101L, 1));
        double baseSlope = NoiseHelper.clamp(0.12 / Math.max(cfg.slopeSmoothness(), 0.01), 0.4, 6.0);
        double slope = baseSlope * Math.pow(9.0, 0.5 - style);    // x3 (cliff) .. x1/3 (ramp)

        double shelfH = SHELF_FRAC * tierH;
        double peakNoise = NoiseHelper.fbm(wx * PEAK_FREQ + 150, wz * PEAK_FREQ - 150, seed + 25L, 2);
        double highH  = tierH * (0.75 + 0.25 * (peakNoise * 0.5 + 0.5));
        double basinH = BASIN_FRAC * tierH;

        double s1 = ramp(field, T_SHELF, shelfH, slope);            // 0 -> 1 up onto the shelf
        double s2 = ramp(field, T_HIGH, highH - shelfH, slope);     // 0 -> 1 up onto the highland
        double s3 = ramp(field, T_BASIN, basinH, slope);            // 0 inside the basin, 1 outside
        double height = shelfH * s1 + (highH - shelfH) * s2 - basinH * (1.0 - s3);

        // Ragged ledges, only where the ground is actually sloping.
        double edge = Math.max(4 * s1 * (1 - s1), Math.max(4 * s2 * (1 - s2), 4 * s3 * (1 - s3)));
        double bumpy = 0.6 + 0.8 * style;                            // ramps are bumpier than cliffs
        double ledges = NoiseHelper.fbm(x * LEDGE_FREQ, z * LEDGE_FREQ, seed + 33L, 2)
                * edge * tierH * LEDGE_FRAC * roughness * bumpy;

        // Floor texture and a slight tilt so tier tops are not perfectly level.
        double foothills = NoiseHelper.fbm(wx * FOOT_FREQ, wz * FOOT_FREQ, seed + 31L, 2) * FOOT_AMP * roughness;
        double micro = NoiseHelper.fbm(x * MICRO_FREQ, z * MICRO_FREQ, seed + 51L, 1) * MICRO_AMP;
        double tilt = TILT_FRAC * tierH * landform;

        int y = (int) Math.round(cfg.surfaceY() + height + ledges + foothills + micro + tilt);
        // Guard against worlds shorter than 24 blocks: keep lo <= hi so the clamp never
        // returns a value outside [minY, maxY].
        int lo = Math.min(minY + 16, maxY - 1);
        int hi = Math.max(lo, maxY - 8);
        return Math.max(lo, Math.min(hi, y));
    }

    /**
     * Smooth step of the field at {@code threshold}. The step is {@code dh} blocks tall and is
     * made just wide enough that its average steepness is {@code slope} blocks per block.
     */
    private static double ramp(double field, double threshold, double dh, double slope) {
        double halfWidth = Math.max(MIN_RAMP, dh * GRAD_REF / (2.0 * slope));
        return NoiseHelper.smoothstep(threshold - halfWidth, threshold + halfWidth, field);
    }

    // =========================================================================
    // Decoration helpers
    // =========================================================================

    private boolean placeVanillaOak(WorldGenSettings settings, WorldInfo info, Random random,
                                    LimitedRegion region, int x, int z) {
        int minY = info.getMinHeight();
        int maxY = info.getMaxHeight();
        int surface = settings.surfaceAt(ArenaBiome.PLAINS, info.getSeed(), x, z, minY, maxY);
        if (surface + 14 >= maxY || surface - 1 < minY
                || !region.isInRegion(x, surface + 1, z)
                || region.getType(x, surface, z) != Material.GRASS_BLOCK
                || !region.getType(x, surface + 1, z).isAir()) {
            return false;
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int neighbourY = settings.surfaceAt(ArenaBiome.PLAINS, info.getSeed(), x + dx, z + dz,
                        minY, maxY);
                if (Math.abs(neighbourY - surface) > 5) {
                    return false;
                }
            }
        }

        TreeType treeType = random.nextDouble() < BIG_OAK_CHANCE ? TreeType.BIG_TREE : TreeType.TREE;
        return region.generateTree(new Location(null, x, surface + 1, z), random, treeType);
    }

    private void placeGrassPatches(WorldGenSettings settings, WorldInfo info, Random random,
                                   LimitedRegion region, int baseX, int baseZ) {
        long seed = info.getSeed();
        for (int patch = 0; patch < GRASS_PATCHES_PER_CHUNK; patch++) {
            int centreX = baseX + random.nextInt(16);
            int centreZ = baseZ + random.nextInt(16);
            for (int i = 0; i < GRASS_ATTEMPTS_PER_PATCH; i++) {
                int x = centreX + random.nextInt(11) - 5;
                int z = centreZ + random.nextInt(11) - 5;
                double chance = patchChance(x, z, centreX, centreZ, GRASS_RADIUS,
                        GRASS_CENTRE_CHANCE, GRASS_EDGE_CHANCE);
                // Low-frequency density variation keeps neighbouring patches from
                // sharing the same perfectly flat coverage.
                double density = 0.65 + 0.7 * NoiseHelper.valueNoise(
                        x * 0.075, z * 0.075, seed + 0x6A55L);
                if (random.nextDouble() < chance * density) {
                    placeGrass(settings, info, random, region, x, z);
                }
            }
        }
    }

    private void placeFlowerPatch(WorldGenSettings settings, WorldInfo info, Random random,
                                  LimitedRegion region, int baseX, int baseZ) {
        if (random.nextDouble() >= FLOWER_PATCH_CHANCE_PER_CHUNK) {
            return;
        }
        long seed = info.getSeed();
        int centreX = baseX + random.nextInt(16);
        int centreZ = baseZ + random.nextInt(16);

        // One dominant flower with a secondary type blended in by noise, instead of a
        // single strict monoculture across the whole patch.
        int primaryIndex = random.nextInt(PLAINS_FLOWERS.length);
        Material primary = PLAINS_FLOWERS[primaryIndex];
        Material secondary = PLAINS_FLOWERS[(primaryIndex + 1
                + random.nextInt(PLAINS_FLOWERS.length - 1)) % PLAINS_FLOWERS.length];

        for (int i = 0; i < FLOWER_ATTEMPTS_PER_PATCH; i++) {
            int x = centreX + random.nextInt(9) - 4;
            int z = centreZ + random.nextInt(9) - 4;
            double chance = patchChance(x, z, centreX, centreZ, FLOWER_RADIUS,
                    FLOWER_CENTRE_CHANCE, FLOWER_EDGE_CHANCE);
            if (random.nextDouble() >= chance) {
                continue;
            }
            double mix = NoiseHelper.fbm(x * 0.11, z * 0.11, seed + 0x5EEDL, 1);
            Material flower = mix > FLOWER_MIX_THRESHOLD ? secondary : primary;
            placeGroundPlant(settings, info, region, x, z, flower);
        }
    }

    private void placePumpkinPatch(WorldGenSettings settings, WorldInfo info, Random random,
                                   LimitedRegion region, int baseX, int baseZ) {
        if (random.nextDouble() >= PUMPKIN_PATCH_CHANCE_PER_CHUNK) {
            return;
        }
        int centreX = baseX + random.nextInt(16);
        int centreZ = baseZ + random.nextInt(16);
        int target = random.nextDouble() < PUMPKIN_PAIR_CHANCE ? 2 : 1;
        int placed = 0;
        for (int i = 0; i < PUMPKIN_ATTEMPTS_PER_PATCH && placed < target; i++) {
            int x = centreX + random.nextInt(5) - 2;
            int z = centreZ + random.nextInt(5) - 2;
            double chance = patchChance(x, z, centreX, centreZ, PUMPKIN_RADIUS,
                    PUMPKIN_CENTRE_CHANCE, PUMPKIN_EDGE_CHANCE);
            if (random.nextDouble() >= chance) {
                continue;
            }
            int surface = settings.surfaceAt(ArenaBiome.PLAINS, info.getSeed(), x, z,
                    info.getMinHeight(), info.getMaxHeight());
            if (!region.isInRegion(x, surface + 1, z)
                    || region.getType(x, surface, z) != Material.GRASS_BLOCK
                    || !region.getType(x, surface + 1, z).isAir()) {
                continue;
            }
            region.setType(x, surface + 1, z, Material.PUMPKIN);
            placed++;
        }
    }

    private void placeGrass(WorldGenSettings settings, WorldInfo info, Random random,
                            LimitedRegion region, int x, int z) {
        int surface = settings.surfaceAt(ArenaBiome.PLAINS, info.getSeed(), x, z,
                info.getMinHeight(), info.getMaxHeight());
        if (!region.isInRegion(x, surface + 1, z)
                || region.getType(x, surface, z) != Material.GRASS_BLOCK
                || !region.getType(x, surface + 1, z).isAir()) {
            return;
        }

        if (random.nextDouble() < TALL_GRASS_CHANCE
                && region.isInRegion(x, surface + 2, z)
                && region.getType(x, surface + 2, z).isAir()) {
            Bisected lower = (Bisected) Material.TALL_GRASS.createBlockData();
            lower.setHalf(Bisected.Half.BOTTOM);
            Bisected upper = (Bisected) Material.TALL_GRASS.createBlockData();
            upper.setHalf(Bisected.Half.TOP);
            region.setBlockData(x, surface + 1, z, lower);
            region.setBlockData(x, surface + 2, z, upper);
        } else {
            region.setType(x, surface + 1, z, Material.SHORT_GRASS);
        }
    }

    private void placeGroundPlant(WorldGenSettings settings, WorldInfo info, LimitedRegion region,
                                  int x, int z, Material plant) {
        int surface = settings.surfaceAt(ArenaBiome.PLAINS, info.getSeed(), x, z,
                info.getMinHeight(), info.getMaxHeight());
        if (region.isInRegion(x, surface + 1, z)
                && region.getType(x, surface, z) == Material.GRASS_BLOCK
                && region.getType(x, surface + 1, z).isAir()) {
            region.setType(x, surface + 1, z, plant);
        }
    }

    /**
     * Placement chance at a point inside a circular patch.  Density fades from
     * {@code centreChance} at the patch centre to {@code edgeChance} at {@code radius},
     * with a soft shoulder so the middle stays clustered without a hard square border.
     */
    private static double patchChance(int x, int z, int centreX, int centreZ,
                                      double radius, double centreChance, double edgeChance) {
        double dx = x - centreX;
        double dz = z - centreZ;
        double distanceSquared = dx * dx + dz * dz;
        double radiusSquared = radius * radius;
        if (distanceSquared >= radiusSquared) {
            return 0.0;
        }
        double t = distanceSquared / radiusSquared;   // 0 at centre, 1 at edge
        double falloff = 1.0 - t * t;
        return edgeChance + (centreChance - edgeChance) * falloff;
    }
}
