package org.pvpcore.features.teleport.rtp;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Typed, clamped view of teleport/rtp.yml. Immutable, so reads never race a reload. */
public record RtpSettings(
        boolean enabled,
        String defaultRegion,
        int warmupSeconds,
        int cooldownSeconds,
        int maxAttempts,
        int columnsPerChunk,
        int borderMargin,
        int maxConcurrentSearches,
        int searchTimeoutSeconds,
        boolean debug,
        boolean poolEnabled,
        int poolSize,
        int poolMaxAgeSeconds,
        int poolRefillIntervalSeconds,
        Set<Material> unsafeBlocks,
        Set<String> blockedBiomes,
        boolean avoidWorldGuardRegions,
        Map<String, RtpRegion> regions) {

    public static RtpSettings from(FileConfiguration yaml, Logger log) {
        Set<Material> unsafe = EnumSet.noneOf(Material.class);
        for (String name : yaml.getStringList("safety.unsafe-blocks")) {
            Material material = Material.matchMaterial(name.trim());
            if (material == null) {
                log.warning("rtp: unknown material '" + name + "' in safety.unsafe-blocks; skipped.");
            } else {
                unsafe.add(material);
            }
        }

        Set<String> biomes = new HashSet<>();
        for (String name : yaml.getStringList("safety.blocked-biomes")) {
            String key = name.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            biomes.add(key.contains(":") ? key : "minecraft:" + key);
        }

        Map<String, RtpRegion> regions = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("regions");
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                ConfigurationSection regionSection = section.getConfigurationSection(rawId);
                if (regionSection == null) {
                    continue;
                }
                String id = rawId.toLowerCase(Locale.ROOT);
                regions.put(id, RtpRegion.parse(id, regionSection, log));
            }
        }

        return new RtpSettings(
                yaml.getBoolean("enabled", true),
                yaml.getString("default-region", "").trim().toLowerCase(Locale.ROOT),
                Math.max(-1, yaml.getInt("warmup-seconds", -1)),
                Math.max(-1, yaml.getInt("cooldown-seconds", -1)),
                clamp(yaml.getInt("search.max-attempts", 12), 1, 100),
                clamp(yaml.getInt("search.columns-per-chunk", 6), 1, 16),
                clamp(yaml.getInt("search.border-margin", 16), 0, 1000),
                clamp(yaml.getInt("search.max-concurrent-searches", 2), 1, 8),
                clamp(yaml.getInt("search.timeout-seconds", 15), 3, 120),
                yaml.getBoolean("search.debug", false),
                yaml.getBoolean("pool.enabled", true),
                clamp(yaml.getInt("pool.size", 4), 0, 32),
                clamp(yaml.getInt("pool.max-age-seconds", 900), 30, 86_400),
                clamp(yaml.getInt("pool.refill-interval-seconds", 10), 2, 300),
                Collections.unmodifiableSet(unsafe),
                Collections.unmodifiableSet(biomes),
                yaml.getBoolean("safety.avoid-worldguard-regions", true),
                Collections.unmodifiableMap(regions));
    }

    /** The region with this id (any case), or null. */
    public RtpRegion region(String id) {
        return id == null ? null : regions.get(id.toLowerCase(Locale.ROOT));
    }

    /** The first region configured for this world, else the default region, else null. */
    public RtpRegion regionFor(String worldName) {
        for (RtpRegion region : regions.values()) {
            if (region.worldName().equalsIgnoreCase(worldName)) {
                return region;
            }
        }
        return defaultRegion.isEmpty() ? null : regions.get(defaultRegion);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
