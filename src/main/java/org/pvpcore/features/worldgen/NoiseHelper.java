package org.pvpcore.features.worldgen;

/**
 * Shared deterministic noise functions used by all terrain profiles and surface helpers.
 * All methods are stateless and thread-safe.
 */
final class NoiseHelper {

    private NoiseHelper() {}

    // -------------------------------------------------------------------------
    // Fractal Brownian Motion (value noise)
    // -------------------------------------------------------------------------

    /** fBm in [-1, 1]; each octave is offset to avoid grid alignment. */
    static double fbm(double x, double z, long seed, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum  += (valueNoise(x * f + i * 31.7, z * f - i * 17.3, seed + i * 1013L) * 2.0 - 1.0) * amp;
            norm += amp;
            amp  *= 0.5;
            f    *= 2.0;
        }
        return sum / norm;
    }

    /** Smooth value noise in [0, 1] at integer lattice points. */
    static double valueNoise(double x, double z, long seed) {
        int x0 = floor(x), z0 = floor(z);
        double tx = x - x0, tz = z - z0;
        tx = tx * tx * (3.0 - 2.0 * tx);
        tz = tz * tz * (3.0 - 2.0 * tz);
        double v00 = hash01(x0,     z0,     seed);
        double v10 = hash01(x0 + 1, z0,     seed);
        double v01 = hash01(x0,     z0 + 1, seed);
        double v11 = hash01(x0 + 1, z0 + 1, seed);
        return mix(mix(v00, v10, tx), mix(v01, v11, tx), tz);
    }

    // -------------------------------------------------------------------------
    // Hash functions
    // -------------------------------------------------------------------------

    /** Deterministic [0, 1) from two integer coordinates and a seed. */
    static double hash01(int x, int z, long seed) {
        long h = murmur(seed ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL);
        return (h & 0xFFFFFFFFL) / (double) 0x100000000L;
    }

    /**
     * Deterministic long seed derived from world seed, integer coordinates, and a
     * biome-/feature-specific salt. Used to seed per-chunk {@link java.util.Random}
     * instances and patch-spacing comparisons.
     */
    static long hashSeed(long seed, int x, int z, long salt) {
        long h = seed ^ salt;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        return murmur(h);
    }

    /** Positional hash in [0, 1) using three integer coordinates. */
    static double positionHash01(int x, int y, int z, long seed) {
        long h = seed;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) z * 0x165667B19E3779F9L;
        return (murmur(h) & 0xFFFFFFFFL) / (double) 0x100000000L;
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    /**
     * Estimates the local slope in blocks/block from the gradient of the height field.
     * Returns the larger of the X and Z finite-difference magnitudes.
     *
     * @param hC  height at the centre column
     * @param hXm height one step in -X
     * @param hXp height one step in +X
     * @param hZm height one step in -Z
     * @param hZp height one step in +Z
     */
    static double maxSlope(int hC, int hXm, int hXp, int hZm, int hZp) {
        return Math.max(
                Math.abs(hXp - hXm) / 2.0,
                Math.abs(hZp - hZm) / 2.0);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double smoothstep(double a, double b, double v) {
        double t = clamp((v - a) / (b - a), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static int floor(double v) { return v < 0 ? (int) v - 1 : (int) v; }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }

    private static long murmur(long h) {
        h ^= (h >>> 33); h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33); h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return h;
    }
}
