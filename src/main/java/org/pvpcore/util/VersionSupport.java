package org.pvpcore.util;

import org.bukkit.Bukkit;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the running Paper/Minecraft version and compares versions numerically.
 *
 * <p>PvPCore is compiled against the oldest supported Paper API (1.21.11) and runs unchanged
 * on every server up to the newest tested build. This class is the single place that knows
 * the supported range, so a future bump only touches {@link #MINIMUM} and {@link #MAXIMUM}.</p>
 *
 * <p>The comparison treats a version as numeric segments ({@code 1.21.11}, {@code 26.1.2},
 * {@code 26.2}) and ignores suffixes such as {@code -pre-2} or {@code -R0.1-SNAPSHOT}. It is
 * deliberately tolerant: an unknown or unparsable version is reported as unsupported but the
 * plugin still enables, because the {@code api-version} in {@code plugin.yml} already stops
 * older servers from loading it.</p>
 */
public final class VersionSupport {

    /** Oldest Paper version whose API the jar is compiled against. */
    public static final String MINIMUM = "1.21.11";
    /** Newest Paper version this build has been verified against. */
    public static final String MAXIMUM = "26.3";

    private static final Pattern FIRST_VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");
    private static final Pattern COMPONENT = Pattern.compile("\\d+");

    private VersionSupport() {
    }

    /** The running server's Minecraft version, for example {@code 1.21.11} or {@code 26.2}. */
    public static String minecraftVersion() {
        try {
            String version = Bukkit.getMinecraftVersion();
            if (version != null && !version.isBlank()) {
                return version.trim();
            }
        } catch (Throwable ignored) {
            // Fall through to the Bukkit version string on very old or unusual builds.
        }
        return parseFromBukkitVersion(Bukkit.getBukkitVersion());
    }

    /** True when the running server is inside the supported range. */
    public static boolean isSupported() {
        return isSupported(minecraftVersion());
    }

    /** True when {@code version} is inside {@link #MINIMUM}..{@link #MAXIMUM}. */
    public static boolean isSupported(String version) {
        return version != null
                && compare(version, MINIMUM) >= 0
                && compare(version, MAXIMUM) <= 0;
    }

    /** True when the running server is at least {@code version}. */
    public static boolean isAtLeast(String version) {
        return compare(minecraftVersion(), version) >= 0;
    }

    /** Logs one line describing the detected version and the supported range. */
    public static void logDetection(Logger logger) {
        if (logger == null) {
            return;
        }
        String version = minecraftVersion();
        if (isSupported(version)) {
            logger.info("Detected Paper " + version + " (supported: " + MINIMUM + " - " + MAXIMUM + ").");
        } else {
            logger.warning("Detected Paper " + version + ", which is outside the tested range "
                    + MINIMUM + " - " + MAXIMUM + ". PvPCore will keep running, but some mechanics "
                    + "may need a compatibility update.");
        }
    }

    /**
     * Compares two Minecraft-style versions by numeric segments.
     *
     * @return negative when {@code left < right}, zero when equal, positive when
     *         {@code left > right}
     */
    public static int compare(String left, String right) {
        int[] leftParts = parts(left);
        int[] rightParts = parts(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int l = i < leftParts.length ? leftParts[i] : 0;
            int r = i < rightParts.length ? rightParts[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int[] parts(String version) {
        if (version == null) {
            return new int[0];
        }
        Matcher matcher = FIRST_VERSION.matcher(version.trim());
        if (!matcher.find()) {
            return new int[0];
        }
        int count = matcher.group(3) != null ? 3 : matcher.group(2) != null ? 2 : 1;
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            String part = matcher.group(i + 1);
            result[i] = part == null ? 0 : parseInt(part);
        }
        return result;
    }

    private static int parseInt(String value) {
        Matcher matcher = COMPONENT.matcher(value);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException overflow) {
            return Integer.MAX_VALUE;
        }
    }

    private static String parseFromBukkitVersion(String bukkitVersion) {
        if (bukkitVersion == null || bukkitVersion.isBlank()) {
            return "unknown";
        }
        int dash = bukkitVersion.indexOf('-');
        return dash > 0 ? bukkitVersion.substring(0, dash) : bukkitVersion;
    }
}
