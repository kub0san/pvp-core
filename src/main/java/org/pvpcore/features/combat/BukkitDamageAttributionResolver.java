package org.pvpcore.features.combat;

import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.Tag;
import org.bukkit.tag.DamageTypeTags;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves damage events against the Paper (1.21.11+) damage-source API.
 *
 * <p>The causing entity (the shooter/igniter) is preferred for player credit; the direct entity is
 * used to retain the source type (projectile vs. melee vs. explosion). Positioned damage with no
 * entity source is matched against the tracked respawn-anchor explosions. Damage that cannot be
 * reliably attributed to a player is environmental/unknown - the nearest player is never credited.</p>
 */
public final class BukkitDamageAttributionResolver
        implements DamageAttributionResolver {

    private final AnchorAttributionTracker anchorTracker;

    public BukkitDamageAttributionResolver(AnchorAttributionTracker anchorTracker) {
        this.anchorTracker = anchorTracker;
    }

    @Override
    public Optional<CombatContext> resolve(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return Optional.empty();
        }
        // Self-guards so direct resolver callers get the same "qualifying hit" semantics;
        // the listener applies the same filters before resolving. !(x > 0) also rejects NaN.
        if (event.isCancelled() || !(event.getFinalDamage() > 0.0D)) {
            return Optional.empty();
        }

        DamageSource source = event.getDamageSource();
        if (source == null) {
            return Optional.empty();
        }

        Entity causing = source.getCausingEntity();
        Entity direct = source.getDirectEntity();

        UUID attackerId = null;
        CombatCause cause = CombatCause.UNKNOWN;
        Attribution attribution = Attribution.UNKNOWN;

        if (causing instanceof Player causingPlayer) {
            // Prefer the causing entity for credit; use the direct entity to keep the source type.
            attackerId = causingPlayer.getUniqueId();
            if (direct == null || direct == causingPlayer) {
                cause = CombatCause.PLAYER_MELEE;
                attribution = Attribution.DIRECT;
            } else if (direct instanceof Projectile) {
                cause = CombatCause.PLAYER_PROJECTILE;
                attribution = Attribution.INDIRECT;
            } else {
                // Entity explosions and similar indirect sources (e.g. primed TNT, attributed
                // wind-charge explosions).
                cause = CombatCause.PLAYER_EXPLOSIVE;
                attribution = Attribution.INDIRECT;
            }
        } else if (direct instanceof Player directPlayer) {
            // No player-caused attribution from the causing entity, but the direct source is a
            // player (plain melee hit where Paper reports no separate causing entity).
            attackerId = directPlayer.getUniqueId();
            cause = CombatCause.PLAYER_MELEE;
            attribution = Attribution.DIRECT;
        } else if (direct instanceof Projectile projectile) {
            // Causing entity not reported (or not a player): fall back to the shooter/owner.
            UUID shooter = resolveProjectileShooter(projectile);
            if (shooter != null) {
                attackerId = shooter;
                cause = CombatCause.PLAYER_PROJECTILE;
                attribution = Attribution.INDIRECT;
            }
        } else if (causing == null && direct == null) {
            // Positioned damage, e.g. an anchor explosion: only credit a tracked, unambiguous
            // explosion recorded at this origin. Restricted to explosion-family damage types, so
            // fall/fire/void/drowning ticks (which can also report a positioned source) can never
            // attribute to an anchor, regardless of what getDamageLocation() returns on a future
            // Paper build.
            if (isExplosionDamage(source)) {
                Location origin = source.getDamageLocation();
                if (origin != null) {
                    if (origin.getWorld() == null && victim.getWorld() != null) {
                        // Paper 26.3 reports the anchor explosion with a positioned source whose
                        // world is null; the victim is always in the world the damage occurred in.
                        origin = new Location(victim.getWorld(), origin.getX(), origin.getY(), origin.getZ());
                    }
                    Optional<UUID> owner = anchorTracker.findRecentOwner(origin);
                    if (owner.isPresent()) {
                        attackerId = owner.get();
                        cause = CombatCause.PLAYER_EXPLOSIVE;
                        attribution = Attribution.INDIRECT;
                    }
                }
            }
        }

        if (attackerId == null || attackerId.equals(victim.getUniqueId())) {
            return Optional.empty();
        }

        return Optional.of(new CombatContext(
                victim.getUniqueId(),
                attackerId,
                cause,
                attribution
        ));
    }

    /**
     * The player responsible for this projectile, or null when it cannot be determined.
     * Dispensers, blocks and non-player sources yield null; projectiles with no player shooter
     * are never credited.
     */
    private UUID resolveProjectileShooter(Projectile projectile) {
        ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
    }

    /**
     * Whether this positioned damage source is an explosion-family type ({@code #minecraft:is_explosion},
     * which includes vanilla's {@code bad_respawn_point}).
     *
     * <p>Uses the experimental {@link DamageTypeTags#IS_EXPLOSION} tag (present on every Paper version
     * this plugin targets). If the type or tag cannot be resolved (e.g. no running server), the
     * pre-guard behavior is kept rather than disabling anchor attribution outright. Exposed
     * package-private so {@link CombatListener} can reuse the same check for the explosion trigger.</p>
     */
    static boolean isExplosionDamage(DamageSource source) {
        // A null source is not evidence of an explosion; it means we don't know what hit them.
        if (source == null) {
            return false;
        }
        try {
            DamageType type = source.getDamageType(); // @NotNull in production; null in offline tests
            if (type == null) {
                return true;
            }
            Tag<DamageType> tag = DamageTypeTags.IS_EXPLOSION; // @Nullable; @ApiStatus.Experimental
            if (tag == null) {
                return true;
            }
            return tag.isTagged(type);
        } catch (Throwable unavailable) {
            // Tag or registry unavailable (e.g. no running server): keep the pre-guard behavior.
            return true;
        }
    }
}
