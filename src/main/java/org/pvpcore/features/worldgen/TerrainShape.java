package org.pvpcore.features.worldgen;

/**
 * The desert arena surface: three flat tiers (low / mid / high) joined by gentle, rough slopes.
 *
 * <p>Pure math (no Bukkit), so the chunk generator and the {@link Decorator} get exactly the
 * same height for any column.</p>
 *
 * <p>How it works: one broad noise value {@code n} picks the tier. Smoothsteps turn it into a
 * mid plateau, a high plateau and a low plateau with smooth ramps between them. Every
 * region gets its own plateau height and plateau size, so no two plateaus look alike. Soft
 * dunes keep the flats from looking like glass, and extra roughness is added only on the
 * ramps, which breaks them into ledges instead of one smooth plane.</p>
 */
final class TerrainShape {

    private TerrainShape() {}

    // ---- Tier selection ----
    private static final double TIER_FREQUENCY   = 0.0055;  // Base frequency for basins (4-6 chunks)
    private static final double PEAK_FREQUENCY   = 0.012;   // High-frequency for small hill clusters (~3-4 chunks)
    private static final double WARP_FREQUENCY   = 0.012;
    private static final double WARP_STRENGTH    = 85.0;    // aggressive warping for twisting terrain borders
    private static final double BAND_HALF        = 0.35;    // Mid tier is now ~50% of the noise range
    private static final double BAND_VARIATION   = 0.10;    // Slight variation in width per region
    private static final double TIER_VARIATION   = 0.45;    // each region's plateau varies significantly

    // ---- Surface texture ----
    private static final double DUNE_FREQUENCY   = 0.025;
    private static final double DUNE_AMPLITUDE   = 2.8;
    private static final double MICRO_FREQUENCY  = 0.090;
    private static final double MICRO_AMPLITUDE  = 2.8;
    private static final double SLOPE_FREQUENCY  = 0.025;
    private static final double SLOPE_AMPLITUDE  = 0.20;    // softer, more gradual slope transitions

    /**
     * @param baseY      the mid tier's height (config surface-y)
     * @param tierHeight average distance from the mid tier to the high and low tiers
     * @param smoothness ramp width in noise units; larger = longer, gentler ramps
     * @param roughness  0 = clean terraces, 1 = default, 2 = very rugged
     */
    static int surfaceY(int baseY, double tierHeight, double smoothness, double roughness,
                        long seed, int x, int z, int minY, int maxY) {
        // Keep the desert profile consistent with the plains profile, which clamps
        // tier-height to [4, 40] before use.
        tierHeight = NoiseHelper.clamp(tierHeight, 4.0, 40.0);

        // 1. Wander the coordinates so tier borders are irregular.
        double wx = x + fbm(x * WARP_FREQUENCY, z * WARP_FREQUENCY, seed + 11, 2) * WARP_STRENGTH;
        double wz = z + fbm(x * WARP_FREQUENCY + 71.3, z * WARP_FREQUENCY - 33.7, seed + 12, 2) * WARP_STRENGTH;

        // 2. Tier selector, and how wide the mid plateau is around here.
        double n = fbm(wx * TIER_FREQUENCY, wz * TIER_FREQUENCY, seed, 3);
        double band = BAND_HALF + BAND_VARIATION * fbm(x * 0.0017 + 500, z * 0.0017 - 500, seed + 3, 1);
        double w = smoothness;

        double up   = smoothstep(band - w, band + w, n);     // 0 -> 1 across the mid/high ramp
        double down = smoothstep(-band - w, -band + w, n);   // 0 -> 1 across the low/mid ramp

        // 3. Plateau heights vary from region to region.
        double highH = tierHeight * (1 + TIER_VARIATION * fbm(wx * 0.0015 + 90, wz * 0.0015, seed + 21, 1));
        double lowH  = tierHeight * (1 + TIER_VARIATION * fbm(wx * 0.0015, wz * 0.0015 - 90, seed + 22, 1));
        double height = highH * up - lowH * (1 - down);

        // 4. 0 on plateaus, 1 in the middle of a ramp.
        double edge = 4 * Math.max(up * (1 - up), down * (1 - down));

        // 5. Texture.
        double dunes = fbm(wx * DUNE_FREQUENCY, wz * DUNE_FREQUENCY, seed + 32, 2) * DUNE_AMPLITUDE;
        double micro = fbm(x * MICRO_FREQUENCY, z * MICRO_FREQUENCY, seed + 31, 2) * MICRO_AMPLITUDE;
        double slope = fbm(x * SLOPE_FREQUENCY, z * SLOPE_FREQUENCY, seed + 33, 2)
                * tierHeight * SLOPE_AMPLITUDE * edge;

        int y = (int) Math.round(baseY + height + (dunes + micro + slope) * roughness);

        // Keep room for bedrock/sand below and for decoration above.
        int lo = Math.min(minY + 16, maxY - 1);
        int hi = Math.max(lo, maxY - 8);
        return Math.max(lo, Math.min(hi, y));
    }

    // ------------------------------------------------------------------ noise

    private static double smoothstep(double a, double b, double v) {
        double t = (v - a) / (b - a);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }

    /** Fractal value noise in [-1, 1]; each octave is offset so octaves don't line up. */
    private static double fbm(double x, double z, long seed, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += (valueNoise(x * f + i * 31.7, z * f - i * 17.3, seed + i * 1013L) * 2.0 - 1.0) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.0;
        }
        return sum / norm;
    }

    private static double valueNoise(double x, double z, long seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = x - x0;
        double tz = z - z0;
        tx = tx * tx * (3.0 - 2.0 * tx);
        tz = tz * tz * (3.0 - 2.0 * tz);

        double v00 = hash01(x0, z0, seed);
        double v10 = hash01(x0 + 1, z0, seed);
        double v01 = hash01(x0, z0 + 1, seed);
        double v11 = hash01(x0 + 1, z0 + 1, seed);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }

    private static double hash01(int x, int z, long seed) {
        long h = seed;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return (h & 0xFFFFFFFFL) / (double) 0x100000000L;
    }
}
