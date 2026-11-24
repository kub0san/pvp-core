package org.pvpcore.features.combat;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Short-lived, block-keyed tracking of respawn-anchor interactions and the explosions they cause.
 *
 * <p>Attribution works by matching an explosion to the interaction at the exact same anchor block;
 * damage is only credited when its origin is within {@code anchor-attribution-radius-blocks} of a
 * recorded explosion at that block. No proximity-based ("nearest player") guessing is ever
 * used: ambiguous matches attribute to nobody.</p>
 *
 * <p>Records expire after {@code anchor-attribution-window-seconds} and are purged by
 * {@link #cleanupExpired()}, which the feature's single shared cleanup task calls periodically.
 * {@link #clear()} drops everything on shutdown. All methods must be called from the server
 * thread; calling them off it throws {@link IllegalStateException}.</p>
 *
 * <p>Note on scale: matching an explosion is O(number of interaction records). At the default
 * 3 second window this is trivial; the configured ceiling (60 seconds, see {@link CombatConfig})
 * keeps that bound regardless of tuning.</p>
 */
public final class AnchorAttributionTracker {

    /** A recent right-click interaction with a specific anchor block. */
    private record InteractionRecord(UUID playerId, UUID worldId, int x, int y, int z, long atNanos) {}

    /** A confirmed anchor explosion with its unambiguous owner, at block-centre coordinates. */
    private record ExplosionRecord(UUID worldId, double x, double y, double z, UUID owner, long atNanos) {}

    // Main-thread only; replaced by reload().
    private long windowNanos;
    private double radiusBlocks;

    private final List<InteractionRecord> interactions = new ArrayList<>();
    private final List<ExplosionRecord> explosions = new ArrayList<>();

    public AnchorAttributionTracker(CombatConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        applyConfig(config);
    }

    /** Swaps in a new validated config (e.g. after {@code /pvp-core reload}). Main thread only. */
    public void reload(CombatConfig newConfig) {
        CombatManager.checkServerThread();
        if (newConfig != null) {
            applyConfig(newConfig);
        }
    }

    private void applyConfig(CombatConfig config) {
        this.windowNanos = config.anchorAttributionWindowNanos();
        this.radiusBlocks = config.anchorAttributionRadiusBlocks();
    }

    /** Records that {@code playerId} right-clicked the anchor block at {@code anchorLocation}. */
    public void recordInteraction(UUID playerId, Location anchorLocation) {
        CombatManager.checkServerThread();
        if (playerId == null) {
            return;
        }
        BlockPosition position = blockPosition(anchorLocation);
        if (position == null) {
            return;
        }
        interactions.add(new InteractionRecord(playerId, position.worldId(), position.x(), position.y(),
                position.z(), System.nanoTime()));
    }

    /**
     * Records a confirmed explosion of the anchor at {@code anchorLocation}.
     *
     * <p>The explosion is only recorded when exactly one player has a recent interaction at this
     * exact block. Zero recent interactions or interactions from more than one distinct player
     * leave the event unattributed; no record is stored and damage from it resolves as unknown.</p>
     */
    public void recordExplosion(Location anchorLocation) {
        CombatManager.checkServerThread();
        BlockPosition position = blockPosition(anchorLocation);
        if (position == null) {
            return;
        }
        long now = System.nanoTime();

        Set<UUID> recentPlayers = new HashSet<>();
        for (InteractionRecord record : interactions) {
            if (isExpired(record.atNanos(), now)) {
                continue;
            }
            if (!record.worldId().equals(position.worldId())
                    || record.x() != position.x() || record.y() != position.y() || record.z() != position.z()) {
                continue;
            }
            recentPlayers.add(record.playerId());
            if (recentPlayers.size() > 1) {
                break; // already ambiguous; no record will be kept
            }
        }

        if (recentPlayers.size() != 1) {
            return;
        }
        explosions.add(new ExplosionRecord(position.worldId(), position.x() + 0.5D,
                position.y() + 0.5D, position.z() + 0.5D, recentPlayers.iterator().next(), now));
    }

    /**
     * Finds the player credited with a recently recorded anchor explosion near {@code damageOrigin}.
     *
     * <p>Returns empty when the origin has no world, or when no explosion record matches, or when
     * matching records disagree on the owner (ambiguous). The caller is responsible for filling in
     * the victim's world when the damage source reports a positioned location without one (which
     * is what Paper 26.3 does for {@code bad_respawn_point} - the location has the block position
     * but a null world).</p>
     */
    public Optional<UUID> findRecentOwner(Location damageOrigin) {
        CombatManager.checkServerThread();
        if (damageOrigin == null) {
            return Optional.empty();
        }
        World world = damageOrigin.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        long now = System.nanoTime();
        UUID owner = null;
        for (ExplosionRecord record : explosions) {
            if (isExpired(record.atNanos(), now)) {
                continue;
            }
            if (!record.worldId().equals(world.getUID())) {
                continue;
            }
            double dx = record.x() - damageOrigin.getX();
            double dy = record.y() - damageOrigin.getY();
            double dz = record.z() - damageOrigin.getZ();
            if (dx * dx + dy * dy + dz * dz > radiusBlocks * radiusBlocks) {
                continue;
            }
            UUID matched = record.owner();
            if (owner == null) {
                owner = matched;
            } else if (!owner.equals(matched)) {
                return Optional.empty(); // direct conflict: no confident attribution
            }
        }
        return Optional.ofNullable(owner);
    }

    /** Drops interaction and explosion records older than the attribution window. */
    public void cleanupExpired() {
        CombatManager.checkServerThread();
        long now = System.nanoTime();
        interactions.removeIf(record -> isExpired(record.atNanos(), now));
        explosions.removeIf(record -> isExpired(record.atNanos(), now));
    }

    /** Drops every transient record. Used on feature shutdown. */
    public void clear() {
        CombatManager.checkServerThread();
        interactions.clear();
        explosions.clear();
    }

    private boolean isExpired(long atNanos, long nowNanos) {
        // Window is capped at 60s by config validation, so this subtraction cannot wrap.
        return nowNanos - atNanos > windowNanos;
    }

    private static BlockPosition blockPosition(Location location) {
        if (location == null) {
            return null;
        }
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        return new BlockPosition(world.getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private record BlockPosition(UUID worldId, int x, int y, int z) {}
}
