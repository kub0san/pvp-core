package org.pvpcore.features.combat;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/**
 * Typed, validated view of {@code combat/config.yml} in the plugin data folder.
 *
 * <p>Fresh instances are created whenever the feature is enabled and whenever {@code /pvpcore reload}
 * runs; the live feature objects are swapped to the new values at that point.</p>
 *
 * <p>All numeric values must stay inside sane bounds:
 * {@code tag-duration-seconds} in (0, {@value #MAX_TAG_DURATION_SECONDS}],
 * {@code anchor-attribution-window-seconds} in (0, {@value #MAX_ANCHOR_WINDOW_SECONDS}],
 * {@code anchor-attribution-radius-blocks} in (0, {@value #MAX_ANCHOR_RADIUS_BLOCKS}], and
 * {@code triggers.combat-zone-radius} in (0, {@value #MAX_COMBAT_ZONE_RADIUS_BLOCKS}].
 * Missing keys fall back to the documented defaults; present-but-invalid or out-of-bounds
 * values (non-numeric, zero, negative, NaN/infinite, above the ceiling) are logged and replaced
 * by the defaults rather than silently accepted.</p>
 */
public record CombatConfig(
        boolean enabled,
        long tagDurationNanos,
        long anchorAttributionWindowNanos,
        double anchorAttributionRadiusBlocks,
        PenaltyMode penaltyMode,
        boolean triggerExplosions,
        boolean triggerEnderPearl,
        boolean triggerCombatZone,
        double combatZoneRadiusBlocks,
        boolean safeZoneMoveBlocking) {

    /** Path of the config file relative to the plugin data folder. */
    public static final String RELATIVE_PATH = "combat/config.yml";

    public static final long DEFAULT_TAG_DURATION_SECONDS = 15L;
    public static final long DEFAULT_ANCHOR_WINDOW_SECONDS = 3L;
    public static final double DEFAULT_ANCHOR_RADIUS_BLOCKS = 2.0D;

    /** Defaults for the new keys, exported so the harness can assert them. */
    public static final PenaltyMode DEFAULT_PENALTY_MODE = PenaltyMode.NONE;
    public static final boolean DEFAULT_TRIGGER_EXPLOSIONS = true;
    public static final boolean DEFAULT_TRIGGER_ENDER_PEARL = true;
    public static final boolean DEFAULT_TRIGGER_COMBAT_ZONE = false;
    public static final double DEFAULT_COMBAT_ZONE_RADIUS_BLOCKS = 10.0D;
    public static final boolean DEFAULT_SAFE_ZONE_MOVE_BLOCKING = true;

    /** One hour: plenty for any tagging use case; keeps nanos arithmetic far from overflow. */
    public static final long MAX_TAG_DURATION_SECONDS = 3600L;
    /** One minute: the anchor window only needs to outlive the click-to-explosion loop. */
    public static final long MAX_ANCHOR_WINDOW_SECONDS = 60L;
    /** 10 blocks: above this, "unambiguous anchor" attribution degrades into "nearest anchor". */
    public static final double MAX_ANCHOR_RADIUS_BLOCKS = 10.0D;
    /** 64 blocks: generous zone, still small enough to be a deliberate choice. */
    public static final double MAX_COMBAT_ZONE_RADIUS_BLOCKS = 64.0D;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /**
     * Reads and validates the settings. {@code section} is the whole {@code combat/config.yml}
     * (the keys live at its root). Never throws for bad values.
     */
    public static CombatConfig from(ConfigurationSection section, Logger log) {
        boolean enabled = booleanValue(section, "enabled", true, "combat.enabled", log);
        long tagSeconds = boundedLong(section, "tag-duration-seconds", DEFAULT_TAG_DURATION_SECONDS,
                MAX_TAG_DURATION_SECONDS, "combat.tag-duration-seconds", log);
        long anchorWindowSeconds = boundedLong(section, "anchor-attribution-window-seconds",
                DEFAULT_ANCHOR_WINDOW_SECONDS, MAX_ANCHOR_WINDOW_SECONDS,
                "combat.anchor-attribution-window-seconds", log);
        double anchorRadius = boundedDouble(section, "anchor-attribution-radius-blocks",
                DEFAULT_ANCHOR_RADIUS_BLOCKS, MAX_ANCHOR_RADIUS_BLOCKS,
                "combat.anchor-attribution-radius-blocks", log);
        PenaltyMode penaltyMode = penaltyMode(section, "penalty.mode", DEFAULT_PENALTY_MODE, log);
        boolean triggerExplosions = booleanValue(section, "triggers.explosions",
                DEFAULT_TRIGGER_EXPLOSIONS, "combat.triggers.explosions", log);
        boolean triggerEnderPearl = booleanValue(section, "triggers.ender-pearl",
                DEFAULT_TRIGGER_ENDER_PEARL, "combat.triggers.ender-pearl", log);
        boolean triggerCombatZone = booleanValue(section, "triggers.combat-zone",
                DEFAULT_TRIGGER_COMBAT_ZONE, "combat.triggers.combat-zone", log);
        double combatZoneRadius = boundedDouble(section, "triggers.combat-zone-radius",
                DEFAULT_COMBAT_ZONE_RADIUS_BLOCKS, MAX_COMBAT_ZONE_RADIUS_BLOCKS,
                "combat.triggers.combat-zone-radius", log);
        boolean safeZoneMoveBlocking = booleanValue(section, "safe-zone-move-blocking",
                DEFAULT_SAFE_ZONE_MOVE_BLOCKING, "combat.safe-zone-move-blocking", log);

        return new CombatConfig(
                enabled,
                tagSeconds * NANOS_PER_SECOND,
                anchorWindowSeconds * NANOS_PER_SECOND,
                anchorRadius,
                penaltyMode,
                triggerExplosions,
                triggerEnderPearl,
                triggerCombatZone,
                combatZoneRadius,
                safeZoneMoveBlocking);
    }

    /** Seconds as a display value for logs: derived from the validated nanoseconds. */
    public long tagDurationSeconds() {
        return tagDurationNanos / NANOS_PER_SECOND;
    }

    /** Seconds as a display value for logs: derived from the validated nanoseconds. */
    public long anchorAttributionWindowSeconds() {
        return anchorAttributionWindowNanos / NANOS_PER_SECOND;
    }

    private static long boundedLong(ConfigurationSection section, String key, long fallback, long max,
            String path, Logger log) {
        Object raw = section == null ? null : section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            warn(log, path, "must be a number, got " + describe(raw) + "; using the default " + fallback);
            return fallback;
        }
        double asDouble = number.doubleValue();
        if (!Double.isFinite(asDouble) || asDouble != Math.rint(asDouble)) {
            warn(log, path, "must be a whole number, got " + describe(raw) + "; using the default " + fallback);
            return fallback;
        }
        long value = number.longValue();
        if (value <= 0L || value > max) {
            warn(log, path, "must be between 1 and " + max + ", got " + value + "; using the default " + fallback);
            return fallback;
        }
        return value;
    }

    private static double boundedDouble(ConfigurationSection section, String key, double fallback, double max,
            String path, Logger log) {
        Object raw = section == null ? null : section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            warn(log, path, "must be a number, got " + describe(raw) + "; using the default " + fallback);
            return fallback;
        }
        double value = number.doubleValue();
        // Double.isFinite rejects NaN and infinities; YAML can produce both (".nan", ".inf").
        if (!Double.isFinite(value) || value <= 0.0D || value > max) {
            warn(log, path, "must be between 0 (exclusive) and " + max + ", got " + value + "; using the default " + fallback);
            return fallback;
        }
        return value;
    }

    private static boolean booleanValue(ConfigurationSection section, String key, boolean fallback,
            String path, Logger log) {
        Object raw = section == null ? null : section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Boolean bool)) {
            warn(log, path, "must be true or false, got " + describe(raw) + "; using the default " + fallback);
            return fallback;
        }
        return bool;
    }

    private static PenaltyMode penaltyMode(ConfigurationSection section, String key, PenaltyMode fallback,
            Logger log) {
        Object raw = section == null ? null : section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof String text)) {
            warn(log, "combat." + key,
                    "must be a string (none, drop or kill), got " + describe(raw) + "; using the default "
                            + fallback.name().toLowerCase(java.util.Locale.ROOT));
            return fallback;
        }
        try {
            return PenaltyMode.valueOf(text.trim().toUpperCase(java.util.Locale.ROOT)
                    .replace('-', '_'));
        } catch (IllegalArgumentException e) {
            warn(log, "combat." + key,
                    "must be one of none, drop, kill (got '" + text + "'); using the default "
                            + fallback.name().toLowerCase(java.util.Locale.ROOT));
            return fallback;
        }
    }

    private static String describe(Object raw) {
        return raw.getClass().getSimpleName() + "(" + raw + ")";
    }

    private static void warn(Logger log, String path, String message) {
        if (log != null) {
            log.warning(path + " " + message + " instead.");
        }
    }
}
