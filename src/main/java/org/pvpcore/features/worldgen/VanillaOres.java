package org.pvpcore.features.worldgen;

import org.bukkit.Material;

import java.util.List;
import java.util.Random;

/**
 * A port of Minecraft's underground generation step: the stone variety blobs (dirt, gravel,
 * granite, diorite, andesite, tuff) and every overworld ore, using vanilla's feature table
 * (attempts per chunk, height distribution, vein size) and vanilla's vein algorithm.
 *
 * <p>Pure logic: it only talks to the world through {@link Blocks}, so it can be tested
 * without a server. {@link UndergroundPopulator} connects it to a Bukkit region.</p>
 *
 * <p>Two things are intentionally not ported because they never matter in an arena with no
 * caves: the "discard when touching air" rule, and the carvers themselves.</p>
 */
final class VanillaOres {

    private VanillaOres() {}

    /** Block access. {@code get} returns null for positions outside the writable area. */
    interface Blocks {
        Material get(int x, int y, int z);
        void set(int x, int y, int z, Material material);
    }

    // ------------------------------------------------------------------ feature table

    private enum Dist { UNIFORM, TRIANGLE }

    /** BLOB replaces any base stone; ORE uses the stone variant in stone-likes, the deepslate variant in deepslate-likes. */
    private enum Target { BLOB, ORE }

    private enum Kind { ABSOLUTE, BOTTOM, TOP }

    private record Anchor(Kind kind, int value) {
        int resolve(int minY, int maxY) {
            return switch (kind) {
                case ABSOLUTE -> value;
                case BOTTOM -> minY + value;      // vanilla: aboveBottom(value)
                case TOP -> maxY - 1 + value;     // vanilla: top()
            };
        }
    }

    private static Anchor abs(int y)    { return new Anchor(Kind.ABSOLUTE, y); }
    private static Anchor bottom(int v) { return new Anchor(Kind.BOTTOM, v); }
    private static Anchor top()         { return new Anchor(Kind.TOP, 0); }

    /**
     * @param countMin attempts per chunk (min); equal to countMax for a fixed count
     * @param rarity   if > 0 the feature runs once in about every {@code rarity} chunks instead
     */
    private record Feature(Material block, Material deepBlock, Target target,
                           int countMin, int countMax, int rarity,
                           Dist dist, Anchor lo, Anchor hi, int size) {}

    private static Feature blob(Material m, int count, int rarity, Dist d, Anchor lo, Anchor hi, int size) {
        return new Feature(m, m, Target.BLOB, count, count, rarity, d, lo, hi, size);
    }

    private static Feature ore(Material stone, Material deep, int count, Dist d, Anchor lo, Anchor hi, int size) {
        return new Feature(stone, deep, Target.ORE, count, count, 0, d, lo, hi, size);
    }

    private static Feature ore(Material stone, Material deep, int countMin, int countMax, Dist d, Anchor lo, Anchor hi, int size) {
        return new Feature(stone, deep, Target.ORE, countMin, countMax, 0, d, lo, hi, size);
    }

    private static Feature rareOre(Material stone, Material deep, int rarity, Dist d, Anchor lo, Anchor hi, int size) {
        return new Feature(stone, deep, Target.ORE, 1, 1, rarity, d, lo, hi, size);
    }

    /** Same order as vanilla's UNDERGROUND_ORES step: blobs first, then ores. */
    private static final List<Feature> FEATURES = List.of(
            // ---- stone variety ----
            blob(Material.DIRT,    7,  0, Dist.UNIFORM, abs(0),     abs(160), 33),
            blob(Material.GRAVEL,  14, 0, Dist.UNIFORM, bottom(0),  top(),    33),
            blob(Material.GRANITE, 1,  6, Dist.UNIFORM, abs(64),    abs(128), 64),   // upper
            blob(Material.GRANITE, 2,  0, Dist.UNIFORM, abs(0),     abs(60),  64),   // lower
            blob(Material.DIORITE, 1,  6, Dist.UNIFORM, abs(64),    abs(128), 64),
            blob(Material.DIORITE, 2,  0, Dist.UNIFORM, abs(0),     abs(60),  64),
            blob(Material.ANDESITE, 1, 6, Dist.UNIFORM, abs(64),    abs(128), 64),
            blob(Material.ANDESITE, 2, 0, Dist.UNIFORM, abs(0),     abs(60),  64),
            blob(Material.TUFF,    2,  0, Dist.UNIFORM, bottom(0),  abs(0),   64),

            // ---- ores ----
            ore(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, 30, Dist.UNIFORM,  abs(136), top(),     17),
            ore(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, 20, Dist.TRIANGLE, abs(0),   abs(192),  17),

            ore(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 90, Dist.TRIANGLE, abs(80),  abs(384),  9),
            ore(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 10, Dist.TRIANGLE, abs(-24), abs(56),   9),
            ore(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 10, Dist.UNIFORM,  bottom(0), abs(72),  4),

            ore(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, 4,  Dist.TRIANGLE, abs(-64), abs(32),   9),
            ore(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, 0, 1, Dist.UNIFORM, abs(-64), abs(-48), 9),

            ore(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, 4, Dist.UNIFORM,  bottom(0),   abs(15),    8),
            ore(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, 8, Dist.TRIANGLE, bottom(-32), bottom(32), 8),

            ore(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 7, Dist.TRIANGLE, bottom(-80), bottom(80), 4),
            ore(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 2, Dist.UNIFORM,  abs(-64),    abs(-4),    8),
            rareOre(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 9, Dist.TRIANGLE, bottom(-80), bottom(80), 12),
            ore(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 4, Dist.TRIANGLE, bottom(-80), bottom(80), 8),

            ore(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, 2, Dist.TRIANGLE, abs(-32),   abs(32), 7),
            ore(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, 4, Dist.UNIFORM,  bottom(0),  abs(64), 7),

            ore(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, 16, Dist.TRIANGLE, abs(-16), abs(112), 10)
    );

    // ------------------------------------------------------------------ driver

    /**
     * Runs every feature for one chunk. Veins start inside the chunk and may reach up to
     * about 13 blocks into the neighbouring chunks, so the {@link Blocks} implementation
     * must cover at least that area (Bukkit's LimitedRegion covers 16).
     */
    static void generate(Blocks blocks, Random random, int chunkX, int chunkZ, int minY, int maxY) {
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;

        for (Feature f : FEATURES) {
            int attempts;
            if (f.rarity() > 0) {
                attempts = random.nextFloat() < 1.0F / f.rarity() ? 1 : 0;
            } else {
                attempts = f.countMin() + (f.countMax() > f.countMin()
                        ? random.nextInt(f.countMax() - f.countMin() + 1) : 0);
            }

            int lo = f.lo().resolve(minY, maxY);
            int hi = f.hi().resolve(minY, maxY);

            for (int i = 0; i < attempts; i++) {
                int x = originX + random.nextInt(16);
                int z = originZ + random.nextInt(16);
                int y = pickHeight(random, f.dist(), lo, hi);
                if (y < minY || y >= maxY) continue; // vanilla skips positions outside the world
                placeVein(blocks, random, f, x, y, z);
            }
        }
    }

    /** Vanilla's uniform and triangle (trapezoid with no plateau) height samplers. */
    private static int pickHeight(Random random, Dist dist, int lo, int hi) {
        if (hi < lo) return lo;
        if (dist == Dist.UNIFORM) {
            return lo + random.nextInt(hi - lo + 1);
        }
        int span = hi - lo;
        int half = span / 2;
        return lo + random.nextInt(span - half + 1) + random.nextInt(half + 1);
    }

    // ------------------------------------------------------------------ vanilla vein algorithm

    /**
     * Vanilla OreFeature: a line segment through the start point, with a chain of spheres
     * along it. Sphere radius swells in the middle of the line, so veins are lumpy worms
     * rather than cubes or slabs. Overlapping spheres are pruned, then every block inside
     * any remaining sphere that is replaceable is changed, each position at most once.
     */
    private static void placeVein(Blocks blocks, Random random, Feature f, int x, int y, int z) {
        int size = f.size();
        float angle = random.nextFloat() * (float) Math.PI;
        float spread = size / 8.0F;
        int pad = (int) Math.ceil(((size / 16.0F * 2.0F) + 1.0F) / 2.0F);

        double x0 = x + Math.sin(angle) * spread;
        double x1 = x - Math.sin(angle) * spread;
        double z0 = z + Math.cos(angle) * spread;
        double z1 = z - Math.cos(angle) * spread;
        double y0 = y + random.nextInt(3) - 2;
        double y1 = y + random.nextInt(3) - 2;

        int startX = x - (int) Math.ceil(spread) - pad;
        int startY = y - 2 - pad;
        int startZ = z - (int) Math.ceil(spread) - pad;
        int width = 2 * ((int) Math.ceil(spread) + pad) + 1;
        int height = 2 * (2 + pad) + 1;

        // Sphere chain: x, y, z, radius per sphere.
        double[] s = new double[size * 4];
        for (int j = 0; j < size; j++) {
            double t = (float) j / (float) size;
            double r = random.nextDouble() * size / 16.0;
            s[j * 4]     = x0 + t * (x1 - x0);
            s[j * 4 + 1] = y0 + t * (y1 - y0);
            s[j * 4 + 2] = z0 + t * (z1 - z0);
            s[j * 4 + 3] = ((Math.sin(Math.PI * t) + 1.0) * r + 1.0) / 2.0;
        }

        // Drop a sphere that is swallowed by a bigger neighbour.
        for (int j = 0; j < size - 1; j++) {
            if (s[j * 4 + 3] <= 0.0) continue;
            for (int k = j + 1; k < size; k++) {
                if (s[k * 4 + 3] <= 0.0) continue;
                double dx = s[j * 4] - s[k * 4];
                double dy = s[j * 4 + 1] - s[k * 4 + 1];
                double dz = s[j * 4 + 2] - s[k * 4 + 2];
                double dr = s[j * 4 + 3] - s[k * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0) s[k * 4 + 3] = -1.0;
                    else s[j * 4 + 3] = -1.0;
                }
            }
        }

        boolean[] done = new boolean[width * height * width];

        for (int j = 0; j < size; j++) {
            double rad = s[j * 4 + 3];
            if (rad < 0.0) continue;
            double cx = s[j * 4], cy = s[j * 4 + 1], cz = s[j * 4 + 2];

            int minX = Math.max((int) Math.floor(cx - rad), startX);
            int minY = Math.max((int) Math.floor(cy - rad), startY);
            int minZ = Math.max((int) Math.floor(cz - rad), startZ);
            int maxX = Math.max((int) Math.floor(cx + rad), minX);
            int maxY = Math.max((int) Math.floor(cy + rad), minY);
            int maxZ = Math.max((int) Math.floor(cz + rad), minZ);

            for (int bx = minX; bx <= maxX; bx++) {
                double ox = (bx + 0.5 - cx) / rad;
                if (ox * ox >= 1.0) continue;
                for (int by = minY; by <= maxY; by++) {
                    double oy = (by + 0.5 - cy) / rad;
                    if (ox * ox + oy * oy >= 1.0) continue;
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        double oz = (bz + 0.5 - cz) / rad;
                        if (ox * ox + oy * oy + oz * oz >= 1.0) continue;

                        int ix = bx - startX, iy = by - startY, iz = bz - startZ;
                        if (ix < 0 || iy < 0 || iz < 0 || ix >= width || iy >= height || iz >= width) continue;
                        int index = (ix * height + iy) * width + iz;
                        if (done[index]) continue;
                        done[index] = true;

                        Material current = blocks.get(bx, by, bz);
                        if (current == null) continue;
                        Material replacement = replacement(f, current);
                        if (replacement != null) blocks.set(bx, by, bz, replacement);
                    }
                }
            }
        }
    }

    private static Material replacement(Feature f, Material current) {
        if (f.target() == Target.BLOB) {
            return isStoneLike(current) || isDeepslateLike(current) ? f.block() : null;
        }
        if (isStoneLike(current)) return f.block();
        if (isDeepslateLike(current)) return f.deepBlock();
        return null;
    }

    /** Vanilla tag STONE_ORE_REPLACEABLES. */
    private static boolean isStoneLike(Material m) {
        return m == Material.STONE || m == Material.GRANITE || m == Material.DIORITE || m == Material.ANDESITE;
    }

    /** Vanilla tag DEEPSLATE_ORE_REPLACEABLES. */
    private static boolean isDeepslateLike(Material m) {
        return m == Material.DEEPSLATE || m == Material.TUFF;
    }
}
