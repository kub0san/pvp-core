package org.pvpcore.features.combat;

/**
 * What caused a qualifying combat hit.
 *
 * <p>New causes should be appended; consumers should tolerate {@link #UNKNOWN} rather than
 * assume the set is closed.</p>
 */
public enum CombatCause {
    PLAYER_MELEE,
    PLAYER_PROJECTILE,
    PLAYER_EXPLOSIVE,
    /** Tagged by proximity (combat-zone trigger): no specific opponent action was observed. */
    PLAYER_PROXIMITY,
    UNKNOWN
}
