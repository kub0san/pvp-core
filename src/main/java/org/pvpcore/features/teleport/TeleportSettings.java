package org.pvpcore.features.teleport;

import org.bukkit.configuration.file.FileConfiguration;

/** Typed, clamped view of teleportation/config.yml. Immutable, so reads never race a reload. */
public record TeleportSettings(
        boolean enabled,
        int requestExpirySeconds,
        int requestCooldownSeconds,
        int warmupSeconds,
        boolean cancelWarmupOnMove,
        int cooldownSeconds,
        boolean respectBedAndAnchor) {

    public static TeleportSettings from(FileConfiguration config) {
        return new TeleportSettings(
                config.getBoolean("enabled", true),
                Math.max(1, config.getInt("requests.expiry-seconds", 120)),
                Math.max(0, config.getInt("requests.cooldown-seconds", 5)),
                Math.max(0, config.getInt("warmup.seconds", 3)),
                config.getBoolean("warmup.cancel-on-move", true),
                Math.max(0, config.getInt("cooldown.seconds", 5)),
                config.getBoolean("respawn.respect-bed-and-anchor", false));
    }
}