package org.pvpcore.features.combat;

import java.util.UUID;

/**
 * The resolved outcome for one damage event that may tag players in combat.
 *
 * <p>{@code attackerId} is only set when attribution is reliable ({@link Attribution#DIRECT} or
 * {@link Attribution#INDIRECT}); {@link Attribution#UNKNOWN} or a {@code null} attacker means the
 * damage must not tag anyone, and {@link CombatManager} ignores such contexts.</p>
 *
 * @param victimId     UUID of the damaged player
 * @param attackerId   UUID of the responsible player, or {@code null} when not confidently attributed
 * @param cause        what caused the hit
 * @param attribution  how reliably the attacker was identified
 */
public record CombatContext(
        UUID victimId,
        UUID attackerId,          // null if not confidently attributed
        CombatCause cause,
        Attribution attribution
) {
    /** True when a player attacker was confidently identified. */
    public boolean isPlayerCaused() {
        return attackerId != null && attribution != Attribution.UNKNOWN;
    }
}
