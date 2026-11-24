package org.pvpcore.mechanic.fastercrystals;

import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps entity ids from interact packets to live crystals, avoiding a world scan per click.
 *
 * <p>Removed crystals are kept for a short delay so a placement packet that arrives just
 * after the destroy still has a position to work from. Expiry is stored as a monotonic
 * timestamp and purged lazily; the mechanic does not schedule one task per destroyed
 * crystal.</p>
 *
 * <p>The map is read from PacketEvents' network thread ({@link #contains(int)} and
 * {@link #size()}), so it is a {@link ConcurrentHashMap}. The cached entity references are
 * only dereferenced on the main thread.</p>
 */
final class CrystalTracker {

    static final long REMOVAL_DELAY_TICKS = 40L;
    static final long REMOVAL_DELAY_NANOS = REMOVAL_DELAY_TICKS * 50_000_000L;

    private record Tracked(EnderCrystal crystal, long expiresAtNanos) {
        boolean expired(long now) {
            return expiresAtNanos != Long.MAX_VALUE && expiresAtNanos <= now;
        }
    }

    private final Map<Integer, Tracked> crystals = new ConcurrentHashMap<>();

    void add(EnderCrystal crystal) {
        crystals.put(crystal.getEntityId(), new Tracked(crystal, Long.MAX_VALUE));
    }

    EnderCrystal get(int entityId) {
        Tracked tracked = crystals.get(entityId);
        if (tracked == null) {
            return null;
        }
        if (tracked.expired(System.nanoTime())) {
            crystals.remove(entityId, tracked);
            return null;
        }
        return tracked.crystal();
    }

    /** Safe on the network thread: it only checks the timestamp, not the entity. */
    boolean contains(int entityId) {
        return get(entityId) != null;
    }

    /** Marks a tracked crystal for lazy removal instead of scheduling a task. */
    void retain(int entityId, long delayNanos) {
        crystals.computeIfPresent(entityId, (id, tracked) -> {
            long now = System.nanoTime();
            long expiresAt = delayNanos <= 0L ? now : now + delayNanos;
            return new Tracked(tracked.crystal(), expiresAt);
        });
    }

    /**
     * True when this entry is a removed crystal kept only for the follow-up placement packet.
     * Such an entry may be used for one placement, then it is retired so duplicate packets
     * cannot place (and consume) a second crystal from the same destroyed entity.
     */
    boolean isRetained(int entityId) {
        Tracked tracked = crystals.get(entityId);
        if (tracked == null) {
            return false;
        }
        long now = System.nanoTime();
        if (tracked.expired(now)) {
            crystals.remove(entityId, tracked);
            return false;
        }
        return tracked.expiresAtNanos() != Long.MAX_VALUE;
    }

    void remove(int entityId) {
        crystals.remove(entityId);
    }

    void removeWorld(World world) {
        UUID worldId = world.getUID();
        crystals.entrySet().removeIf(entry -> {
            try {
                return entry.getValue().crystal().getWorld().getUID().equals(worldId);
            } catch (Throwable ignored) {
                // The entity reference is no longer valid.
                return true;
            }
        });
    }

    void clear() {
        crystals.clear();
    }

    int size() {
        purgeExpired();
        return crystals.size();
    }

    private void purgeExpired() {
        long now = System.nanoTime();
        crystals.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }
}
