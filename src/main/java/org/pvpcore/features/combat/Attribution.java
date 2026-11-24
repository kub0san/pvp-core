package org.pvpcore.features.combat;

/**
 * How reliably the attacker was identified for a qualifying combat hit.
 *
 * <ul>
 *     <li>{@link #DIRECT} - the attacker was the direct source of the damage (e.g. a melee hit).</li>
 *     <li>{@link #INDIRECT} - the attacker was credited through a tracked mechanism such as a
 *     projectile, an attributed entity explosion, or a matched respawn-anchor interaction.</li>
 *     <li>{@link #UNKNOWN} - no reliable player attribution; the damage is environmental or the
 *     responsible player could not be determined.</li>
 * </ul>
 */
public enum Attribution {
    DIRECT,
    INDIRECT,
    UNKNOWN
}
