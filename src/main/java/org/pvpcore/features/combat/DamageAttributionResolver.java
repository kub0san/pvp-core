package org.pvpcore.features.combat;

import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Optional;

/**
 * Resolves a damage event into a {@link CombatContext}, or empty when there is no confident
 * player attribution.
 *
 * <p>Implementations must never infer an attacker from proximity; damage that cannot be reliably
 * attributed to a player resolves as environmental/unknown.</p>
 */
public interface DamageAttributionResolver {
    /** Resolves the context for this damage event, or empty for non-player-caused/unknown damage. */
    Optional<CombatContext> resolve(EntityDamageEvent event);
}
