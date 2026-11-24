package org.pvpcore.features.teleport.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * A validated landing spot: block coordinates of the player's feet plus the world and the time
 * it was found. Immutable. The world is held by id so a pooled spot never keeps an unloaded
 * world alive.
 */
public record RtpSpot(UUID worldId, int x, int y, int z, long createdAtMillis) {

    /** The world, or null if it has been unloaded since the spot was found. */
    public World world() {
        return Bukkit.getWorld(worldId);
    }

    /** The centre of the block, or null if the world is gone. Yaw and pitch are 0. */
    public Location toLocation() {
        World world = world();
        return world == null ? null : new Location(world, x + 0.5, y, z + 0.5);
    }

    public boolean isExpired(long nowMillis, long maxAgeMillis) {
        return nowMillis - createdAtMillis > maxAgeMillis;
    }
}
