package org.pvpcore.features.teleport.rtp;

import org.bukkit.Location;

/**
 * A veto over a candidate landing spot, for example "not inside a WorldGuard region".
 * Called on the main thread only, once per candidate that already passed the block checks.
 */
@FunctionalInterface
public interface LocationVerifier {

    /** Returns true if players may be sent to {@code location}. */
    boolean allows(Location location);
}
