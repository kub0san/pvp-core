package org.pvpcore.mechanic;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared PacketEvents soft-dependency gate.
 *
 * <p>PacketEvents 2.14.0 supports every server version PvPCore targets (1.21.11-26.3) and is
 * the minimum this plugin compiles against. The plugin is optional: mechanics that need it
 * fail closed with a clear warning, while every other mechanic keeps working.</p>
 */
public final class PacketEventsGate {

    // Anchor the expression: searching for the first pair of digits could mistake an
    // unrelated build number (for example, "dev-2026.3") for the plugin version.
    private static final Pattern VERSION = Pattern.compile(
            "^\\s*[vV]?(\\d+)\\.(\\d+)(?:\\.\\d+)?(?:[-+][0-9A-Za-z.-]+)?\\s*$");
    private static final int MIN_MAJOR = 2;
    private static final int MIN_MINOR = 14;

    private PacketEventsGate() {
    }

    /** True when PacketEvents is installed, enabled and at least 2.14.0. */
    public static boolean ready() {
        Plugin packetEvents = packetEvents();
        if (packetEvents == null || !packetEvents.isEnabled()) {
            return false;
        }
        return isSupportedVersion(packetEvents.getPluginMeta().getVersion());
    }

    /** PacketEvents' plugin version, or {@code unknown} when it is absent. */
    public static String version() {
        Plugin packetEvents = packetEvents();
        if (packetEvents == null) {
            return "unknown";
        }
        String version = packetEvents.getPluginMeta().getVersion();
        return version == null ? "unknown" : version;
    }

    /** A short reason suitable for an enable warning. */
    public static String unavailableDetail() {
        Plugin packetEvents = packetEvents();
        if (packetEvents == null) {
            return "PacketEvents is not installed";
        }
        if (!packetEvents.isEnabled()) {
            return "PacketEvents is installed but disabled";
        }
        String version = version();
        if (!isSupportedVersion(version)) {
            return "PacketEvents " + version + " is below the supported version 2.14.0, or its version is unrecognised";
        }
        return "found PacketEvents " + version;
    }

    private static Plugin packetEvents() {
        return Bukkit.getPluginManager().getPlugin("packetevents");
    }

    private static boolean isSupportedVersion(String version) {
        if (version == null) {
            return false;
        }

        Matcher matcher = VERSION.matcher(version);
        if (!matcher.matches()) {
            // Unknown versions must fail closed. This gate is used before calling APIs
            // that require a known minimum PacketEvents release.
            return false;
        }

        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            return major > MIN_MAJOR || (major == MIN_MAJOR && minor >= MIN_MINOR);
        } catch (NumberFormatException malformedVersion) {
            return false;
        }
    }
}
