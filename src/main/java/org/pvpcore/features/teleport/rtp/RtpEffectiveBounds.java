package org.pvpcore.features.teleport.rtp;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Effective, request-vs-border bounds for a region. Used by the search and by admin diagnostics
 * so a small world border is visible instead of silently capping /rtp.
 */
record RtpEffectiveBounds(
        double borderX,
        double borderZ,
        double borderHalf,
        double centerX,
        double centerZ,
        String centerSource,
        int requestedMin,
        int requestedMax,
        int effectiveMin,
        int effectiveMax,
        RtpRegion.Shape shape,
        List<String> problems) {

    /** Cap when a region fills a border that is effectively unset (vanilla default is 30 million). */
    static final double MAX_DERIVED_RADIUS = 30_000.0;

    static RtpEffectiveBounds of(RtpRegion region, World world, int borderMargin) {
        WorldBorder border = world.getWorldBorder();
        Location centre = border.getCenter();
        double borderX = centre.getX();
        double borderZ = centre.getZ();
        double borderHalf = Math.max(0.0, border.getSize() / 2.0 - borderMargin);

        boolean onBorder = region.centerOnBorder();
        double centerX = onBorder ? borderX : region.centerX();
        double centerZ = onBorder ? borderZ : region.centerZ();

        int requestedMin = region.minRadius();
        int requestedMax = region.maxRadius();
        int effectiveMin = requestedMin;
        int effectiveMax;

        if (requestedMax > 0) {
            effectiveMax = requestedMax;
        } else {
            effectiveMax = (int) Math.min(MAX_DERIVED_RADIUS, borderHalf);
        }
        if (effectiveMin > effectiveMax) {
            effectiveMin = effectiveMax;
        }

        List<String> problems = new ArrayList<>();
        if (requestedMax > 0) {
            double toEdgeX = Math.max(0.0, borderHalf - Math.abs(centerX - borderX));
            double toEdgeZ = Math.max(0.0, borderHalf - Math.abs(centerZ - borderZ));
            double fit = Math.min(toEdgeX, toEdgeZ);
            if (fit < requestedMax) {
                problems.add(String.format(Locale.ROOT,
                        "requested max-radius %d exceeds what the world border allows from this centre (%.0f); range is capped",
                        requestedMax, fit));
            }
        }
        if (borderHalf <= 0) {
            problems.add("world border (with search.border-margin) leaves no usable area");
        }
        if (!insideBounds(centerX, centerZ, borderX, borderZ, borderHalf) && effectiveMax > 0) {
            problems.add(String.format(Locale.ROOT,
                    "region centre (%.0f, %.0f) is outside the usable world border (centre %.0f, %.0f half %.0f)",
                    centerX, centerZ, borderX, borderZ, borderHalf));
        }
        if (effectiveMax <= 0) {
            problems.add("effective max radius is 0; nothing to search");
        } else if (effectiveMin > effectiveMax) {
            problems.add("min-radius is greater than effective max-radius");
        }

        return new RtpEffectiveBounds(borderX, borderZ, borderHalf, centerX, centerZ,
                onBorder ? "border" : String.format(Locale.ROOT, "%.0f, %.0f", region.centerX(), region.centerZ()),
                requestedMin, requestedMax, effectiveMin, effectiveMax, region.shape(), List.copyOf(problems));
    }

    Optional<String> problemSummary() {
        if (problems.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join("; ", problems));
    }

    String summary() {
        return String.format(Locale.ROOT,
                "centre=(%.0f, %.0f from %s) requested=%d-%d effective=%d-%d shape=%s border(centre=%.0f, %.0f half=%.0f)",
                centerX, centerZ, centerSource, requestedMin, requestedMax, effectiveMin, effectiveMax, shape,
                borderX, borderZ, borderHalf);
    }

    boolean inside(double x, double z) {
        double px = x; // caller uses block-centre coordinates already
        double pz = z;
        if (Math.abs(px - borderX) > borderHalf || Math.abs(pz - borderZ) > borderHalf) {
            return false;
        }
        double dx = px - centerX;
        double dz = pz - centerZ;
        if (shape == RtpRegion.Shape.CIRCLE) {
            double distanceSquared = dx * dx + dz * dz;
            double minSquared = (double) effectiveMin * effectiveMin;
            double maxSquared = (double) effectiveMax * effectiveMax;
            return distanceSquared >= minSquared && distanceSquared <= maxSquared;
        }
        double chebyshev = Math.max(Math.abs(dx), Math.abs(dz));
        return chebyshev >= effectiveMin && chebyshev <= effectiveMax;
    }

    private static boolean insideBounds(double x, double z, double borderX, double borderZ, double borderHalf) {
        return Math.abs(x - borderX) <= borderHalf && Math.abs(z - borderZ) <= borderHalf;
    }
}
