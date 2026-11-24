package org.pvpcore.features.combat;

/** What happens to a player who logs out while tagged. */
public enum PenaltyMode {
    /** No penalty; the session is simply cleared. */
    NONE,
    /** Drop the inventory and XP where they stood when they logged out. */
    DROP,
    /** Kill them on the spot (death drops as usual). */
    KILL
}
