package org.pvpcore.features.worldgen;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * Slope-aware surface material rules shared by all grass/dirt biomes.
 *
 * <p>Vanilla's surface generation works by computing a per-column dirt depth driven
 * by a noise value, then applying a priority-ordered list of surface rules. We
 * replicate three of those rules here:</p>
 *
 * <ol>
 *   <li><b>Stone outcrop</b> — on the steepest and/or highest columns the dirt
 *       layer is so thin that stone becomes the top block, or breaks through the
 *       bottom of the dirt column. This is what produces the grey rock patches
 *       visible on the upper slopes and cliff faces of tall vanilla hills.</li>
 *   <li><b>No-grass wall</b> — on moderately steep columns dirt replaces
 *       {@code GRASS_BLOCK} as the surface block, producing the brown stepped-
 *       wall look rather than a solid green curve.</li>
 *   <li><b>Normal grass floor</b> — flat columns get {@code GRASS_BLOCK} on top
 *       with a fixed 3-block dirt depth below.</li>
 * </ol>
 *
 * <p>The caller is responsible for computing the local slope and height-above-base
 * values; this class only applies the material rules.</p>
 *
 * <p>{@link #applyVanilla} is the faithful alternative: it ignores slope entirely and
 * reproduces vanilla's own rule, a grass block on every column's top and a noise-driven
 * number of dirt blocks below it, so stone shows only where soil is thin or where a drop
 * is taller than the soil.</p>
 */
final class SurfaceMaterials {

    private SurfaceMaterials() {}

    // ---- Thresholds (blocks per block) ----

    /**
     * Slope at or above which the top block switches from GRASS_BLOCK to bare DIRT.
     * Columns above this look brown rather than green.
     */
    static final double SLOPE_DIRT_TOP  = 2.5;

    /**
     * Slope at or above which stone starts replacing the bottom dirt layers,
     * producing the thin-dirt / stone-patch look on steeper walls.
     */
    static final double SLOPE_THIN_DIRT = 5.0;

    /**
     * Slope at or above which the dirt depth is reduced to 1 and stone replaces
     * the top block entirely on high columns — the "stone outcrop" effect.
     */
    static final double SLOPE_OUTCROP   = 7.0;

    /**
     * Height above the world base (surfaceY config) at or above which stone outcrops
     * become possible even on moderate slopes.  Keeps stone out of foothills so
     * outcrops only appear high on major hills, matching vanilla.
     */
    static final double HEIGHT_OUTCROP_MIN = 10.0;

    /**
     * At this height above base and above, outcrop probability reaches 1 on steep
     * columns.  Between HEIGHT_OUTCROP_MIN and this value the effect blends in.
     */
    static final double HEIGHT_OUTCROP_FULL = 18.0;

    // ---- Normal dirt depth ----

    /** Number of dirt layers below the surface block on gentle terrain. */
    static final int DIRT_LAYERS = 3;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Writes the surface material layers for one column into {@code data}.
     *
     * @param data         chunk being built
     * @param lx           local X (0–15)
     * @param lz           local Z (0–15)
     * @param surface      Y of the top solid block
     * @param worldX       world X (for noise sampling)
     * @param worldZ       world Z (for noise sampling)
     * @param seed         world seed
     * @param slope        local slope in blocks per block (from {@link NoiseHelper#maxSlope})
     * @param heightAboveBase  {@code surface - surfaceYConfig} — 0 on the flat floor
     * @param stoneBase    material used for the column below the dirt zone (usually STONE)
     */
    static void apply(ChunkData data,
                      int lx, int lz, int surface,
                      int worldX, int worldZ, long seed,
                      double slope, double heightAboveBase,
                      Material stoneBase) {

        // --- Dirt depth: fixed 3 blocks on plains terrain.
        int dirtDepth = DIRT_LAYERS;

        // --- Stone outcrop: how strongly it applies at this column.
        double outcroppiness = outcroppiness(slope, heightAboveBase);

        // ---- Top block ----
        Material top;
        if (outcroppiness >= 1.0) {
            top = stoneBase;           // pure stone top — outcrop
        } else if (slope >= SLOPE_DIRT_TOP) {
            top = Material.DIRT;       // brown wall
        } else {
            top = Material.GRASS_BLOCK;
        }
        data.setBlock(lx, surface, lz, top);

        // ---- Dirt column ----
        // On outcrops the column shortens; on cliff walls the bottom layers go stone.
        int dirtLayers = dirtDepth;
        if (outcroppiness >= 1.0) {
            dirtLayers = 0;   // pure stone top: no dirt, column already filled with stoneBase
        } else if (outcroppiness > 0) {
            // Partial outcrop: cut dirt depth; add stone noise patches at the base.
            dirtLayers = Math.max(1, (int) Math.round(dirtDepth * (1.0 - outcroppiness)));
        }

        for (int depth = 1; depth <= dirtDepth; depth++) {
            int y = surface - depth;
            Material mat;
            if (depth > dirtLayers) {
                mat = stoneBase;   // bottom of dirt zone replaced by stone
            } else if (slope >= SLOPE_THIN_DIRT && depth >= dirtLayers - 1) {
                // Thin-dirt rule: stone patches at bottom of dirt on steep faces.
                double patchNoise = NoiseHelper.positionHash01(worldX, y, worldZ, seed + 601L);
                mat = (patchNoise < 0.55) ? stoneBase : Material.DIRT;
            } else {
                mat = Material.DIRT;
            }
            data.setBlock(lx, y, lz, mat);
        }
        // The stone column below is already in place from ChunkGenerator.
    }

    // -------------------------------------------------------------------------
    // Vanilla soil rule
    // -------------------------------------------------------------------------

    /**
     * Vanilla's soil thickness is {@code floor(surface * 2.75 + 3.0 + rand * 0.25)} dirt blocks
     * under the grass, where {@code surface} is a smooth noise with ~16-64 block features.
     * Vanilla's noise has a wider spread than {@link NoiseHelper#fbm}, so it is scaled up
     * to give the same spread of thicknesses.
     */
    static final double SOIL_NOISE_FREQ  = 1.0 / 64.0;
    static final double SOIL_NOISE_SCALE = 1.5;
    static final double SOIL_DEPTH_MEAN  = 3.0;
    static final double SOIL_DEPTH_SWING = 2.75;
    static final int    SOIL_DEPTH_MAX   = 8;

    /** Number of dirt blocks under the grass at a column (0 = grass sits directly on stone). */
    static int soilDepth(int worldX, int worldZ, long seed) {
        double n = NoiseHelper.fbm(worldX * SOIL_NOISE_FREQ, worldZ * SOIL_NOISE_FREQ, seed + 701L, 3)
                * SOIL_NOISE_SCALE;
        double jitter = NoiseHelper.positionHash01(worldX, 0, worldZ, seed + 702L) * 0.25;
        int depth = (int) Math.floor(n * SOIL_DEPTH_SWING + SOIL_DEPTH_MEAN + jitter);
        return Math.max(0, Math.min(SOIL_DEPTH_MAX, depth));
    }

    /**
     * Vanilla plains surface for one column: {@code GRASS_BLOCK} on top, then
     * {@link #soilDepth} dirt blocks, then whatever the caller already placed (stone).
     *
     * <p>Slope is deliberately not an input. Because every column carries its own cap,
     * a staircase slope reads as green ledges and brown risers, and a cliff taller than
     * the cap shows grey stone below it. Where the noise makes the cap thin, stone patches
     * show on slopes.</p>
     */
    static void applyVanilla(ChunkData data, int lx, int lz, int surface,
                             int worldX, int worldZ, long seed) {
        data.setBlock(lx, surface, lz, Material.GRASS_BLOCK);
        int depth = soilDepth(worldX, worldZ, seed);
        for (int d = 1; d <= depth; d++) {
            data.setBlock(lx, surface - d, lz, Material.DIRT);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a value in [0, 1] representing how strongly a stone outcrop applies.
     * 0 = no outcrop (normal dirt/grass), 1 = full stone top.
     */
    private static double outcroppiness(double slope, double heightAboveBase) {
        // Height factor: zero below HEIGHT_OUTCROP_MIN, ramps to 1 at HEIGHT_OUTCROP_FULL.
        double hFactor = NoiseHelper.smoothstep(HEIGHT_OUTCROP_MIN, HEIGHT_OUTCROP_FULL,
                                                heightAboveBase);
        if (hFactor <= 0.0) return 0.0;

        // Slope factor: zero below SLOPE_THIN_DIRT, full at SLOPE_OUTCROP.
        double sFactor = NoiseHelper.smoothstep(SLOPE_THIN_DIRT, SLOPE_OUTCROP, slope);
        if (sFactor <= 0.0) return 0.0;

        return hFactor * sFactor;
    }
}
