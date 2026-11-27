package org.pvpcore.config.kits;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Immutable, validated rules for the kit cosmetics GUI. Parsed once per config load;
 * an empty allowlist means every registry key is allowed.
 */
public record CustomizeRules(
        boolean enabled,
        boolean allowClickEvents,
        boolean allowHoverEvents,
        int maxNameLength,
        Set<String> allowedTrimPatterns,
        Set<String> allowedTrimMaterials) {

    public static final int DEFAULT_MAX_NAME_LENGTH = 256;

    public CustomizeRules {
        allowedTrimPatterns = Set.copyOf(allowedTrimPatterns);
        allowedTrimMaterials = Set.copyOf(allowedTrimMaterials);
    }

    /** Parses the {@code customize} section without emitting warnings. A null section yields defaults. */
    public static CustomizeRules parse(ConfigurationSection section) {
        return parse(section, warning -> {});
    }

    /** Parses the {@code customize} section, reporting unknown keys and bad values via {@code warn}. */
    public static CustomizeRules parse(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        if (section == null) return defaults();

        Set<String> known = Set.of("enabled", "allow-click-events", "allow-hover-events",
                "max-name-length", "allowed-trim-patterns", "allowed-trim-materials");
        for (String key : section.getKeys(false)) {
            if (!known.contains(key)) warn.accept("customize." + key + ": unknown setting (ignored)");
        }

        return new CustomizeRules(
                bool(section, "enabled", true, warn),
                bool(section, "allow-click-events", false, warn),
                bool(section, "allow-hover-events", false, warn),
                clamped(section, "max-name-length", DEFAULT_MAX_NAME_LENGTH, 1, 1024, warn),
                keys(section, "allowed-trim-patterns", warn),
                keys(section, "allowed-trim-materials", warn));
    }

    public boolean allowsTrimPattern(String id) { return allows(allowedTrimPatterns, id); }

    public boolean allowsTrimMaterial(String id) { return allows(allowedTrimMaterials, id); }

    private static CustomizeRules defaults() {
        return new CustomizeRules(true, false, false, DEFAULT_MAX_NAME_LENGTH,
                Set.of(), Set.of());
    }

    private static boolean allows(Set<String> allowed, String id) {
        if (allowed.isEmpty()) return true;
        String key = normalize(id);
        return key != null && allowed.contains(key);
    }

    private static int clamped(ConfigurationSection section, String key, int fallback, int min, int max,
                               Consumer<String> warn) {
        if (!section.contains(key)) return fallback;
        Object raw = section.get(key);
        if (!(raw instanceof Number number)) {
            warn.accept("customize." + key + ": expected a whole number, using " + fallback);
            return fallback;
        }
        return Math.max(min, Math.min(max, number.intValue()));
    }

    private static boolean bool(ConfigurationSection section, String key, boolean fallback, Consumer<String> warn) {
        if (!section.contains(key)) return fallback;
        Object raw = section.get(key);
        if (!(raw instanceof Boolean value)) {
            warn.accept("customize." + key + ": expected true or false, using " + fallback);
            return fallback;
        }
        return value;
    }

    private static Set<String> keys(ConfigurationSection section, String key, Consumer<String> warn) {
        if (!section.contains(key)) return Set.of();
        Object raw = section.get(key);
        if (!(raw instanceof Iterable<?> values)) {
            warn.accept("customize." + key + ": expected a list of registry keys, allowing all");
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : values) {
            if (!(item instanceof String text)) {
                warn.accept("customize." + key + ": expected text registry keys, ignoring " + item);
                continue;
            }
            String normalized = normalize(text);
            if (normalized == null) {
                warn.accept("customize." + key + ": invalid registry key '" + text + "' (ignored)");
                continue;
            }
            result.add(normalized);
        }
        return Set.copyOf(result);
    }

    /** Canonicalizes a registry key; bare names default to the Minecraft namespace. */
    private static String normalize(String value) {
        if (value == null) return null;
        String text = value.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(text);
        if (key == null && !text.contains(":")) key = NamespacedKey.fromString("minecraft:" + text);
        return key == null ? null : key.toString();
    }
}
