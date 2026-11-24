package org.pvpcore.mechanic.shieldfixes;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state for Shield Fixes.
 *
 * <p>The blocked-hit marks and the hand-state cache are written from the main
 * thread and read from PacketEvents' network thread, so every collection here is
 * concurrent. Expired marks are removed lazily; no cleanup task is needed.</p>
 */
final class ShieldState {

    /*
     * Metadata indices are stable across the supported range. Entity registers accessors 0-7
     * in 1.21.11 and 26.3, and LivingEntity's DATA_LIVING_ENTITY_FLAGS is accessor 8 in both.
     * Its flags are:
     *   0x01 using item, 0x02 off hand, 0x04 spin attack.
     * Verified against Paper 1.21.11 and 26.3. Re-check the index when a target version adds
     * a new base Entity accessor.
     */
    static final int HAND_STATE_INDEX = 8;
    static final byte HAND_USING_MAIN = 0x01;
    static final byte HAND_USING_OFF = 0x03;
    static final byte HAND_MASK = 0x03;

    /**
     * A blocked hit's hurt sound is sent synchronously after the damage event.
     * Two server ticks is more than enough for the packet to reach the send
     * listener, and short enough not to affect a later, unrelated hurt sound.
     */
    private static final long BLOCKED_HIT_TTL_NANOS = 100_000_000L;

    private record BlockedHit(UUID worldId, int x8, int y8, int z8, long expiresAtNanos) {

        boolean expired(long nowNanos) {
            return nowNanos - expiresAtNanos >= 0L;
        }

        boolean matches(UUID world, int x, int y, int z) {
            return worldId.equals(world) && x8 == x && y8 == y && z8 == z;
        }
    }

    private final Map<Integer, BlockedHit> blockedHits = new ConcurrentHashMap<>();
    private final Map<Integer, Map<UUID, Byte>> lastSentHand = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerIdsByUuid = new ConcurrentHashMap<>();
    private final Set<Integer> playerEntityIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> pendingResyncs = new ConcurrentHashMap<>();

    /** Marks the victim's next player-hurt sound as a blocked hit. Main thread only. */
    void markBlocked(Player victim, long nowNanos) {
        purgeExpired(nowNanos);
        blockedHits.put(victim.getEntityId(), new BlockedHit(
                victim.getWorld().getUID(),
                quantize(victim.getX()),
                quantize(victim.getY()),
                quantize(victim.getZ()),
                nowNanos + BLOCKED_HIT_TTL_NANOS));
    }

    /**
     * True when a live mark exists at this exact quantized position in this world.
     * Called from the network thread for positional {@code SOUND_EFFECT} packets.
     */
    boolean isBlockedAt(UUID worldId, double x, double y, double z, long nowNanos) {
        purgeExpired(nowNanos);
        int x8 = quantize(x);
        int y8 = quantize(y);
        int z8 = quantize(z);
        for (BlockedHit hit : blockedHits.values()) {
            if (hit.matches(worldId, x8, y8, z8)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when a live mark exists for this entity id. Kept for a server build that sends the
     * hurt sound as {@code ENTITY_SOUND_EFFECT}; the supported Paper builds currently use the
     * positional packet.
     */
    boolean isBlockedEntity(int entityId, long nowNanos) {
        BlockedHit hit = blockedHits.get(entityId);
        if (hit == null) {
            return false;
        }
        if (hit.expired(nowNanos)) {
            blockedHits.remove(entityId, hit);
            return false;
        }
        return true;
    }

    private void purgeExpired(long nowNanos) {
        blockedHits.entrySet().removeIf(entry -> entry.getValue().expired(nowNanos));
    }

    /** Registers a player and replaces a stale entity id after a respawn. Main thread only. */
    void addPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        int entityId = player.getEntityId();
        Integer previous = playerIdsByUuid.put(uuid, entityId);
        if (previous != null && previous != entityId) {
            forgetEntity(previous);
        }
        playerEntityIds.add(entityId);
    }

    /** Removes all state owned by a quitting player. Main thread only. */
    void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Integer entityId = playerIdsByUuid.remove(uuid);
        if (entityId != null) {
            forgetEntity(entityId);
        }
        playerEntityIds.remove(player.getEntityId());
        pendingResyncs.remove(uuid);
        // This player can no longer be a viewer for anyone else.
        for (Map<UUID, Byte> viewers : lastSentHand.values()) {
            viewers.remove(uuid);
        }
    }

    private void forgetEntity(int entityId) {
        playerEntityIds.remove(entityId);
        blockedHits.remove(entityId);
        lastSentHand.remove(entityId);
    }

    boolean isTrackedPlayer(int entityId) {
        return playerEntityIds.contains(entityId);
    }

    /** Records the hand-state byte an outgoing metadata packet actually sent to a viewer. */
    void recordSentHand(int entityId, UUID viewerId, byte flags) {
        if (!playerEntityIds.contains(entityId)) {
            return;
        }
        lastSentHand.computeIfAbsent(entityId, ignored -> new ConcurrentHashMap<>())
                .put(viewerId, flags);
    }

    byte lastSentHand(int entityId, UUID viewerId) {
        Map<UUID, Byte> viewers = lastSentHand.get(entityId);
        Byte flags = viewers == null ? null : viewers.get(viewerId);
        return flags == null ? 0 : flags;
    }

    boolean hasSentHand(int entityId, UUID viewerId) {
        Map<UUID, Byte> viewers = lastSentHand.get(entityId);
        return viewers != null && viewers.containsKey(viewerId);
    }

    /**
     * Coalesces resync requests per player. Returns true when a task must be
     * scheduled; an existing scheduled task is upgraded to forced if needed.
     */
    boolean beginResync(UUID uuid, boolean force) {
        Boolean previous = pendingResyncs.putIfAbsent(uuid, force);
        if (previous == null) {
            return true;
        }
        if (force && !previous) {
            pendingResyncs.replace(uuid, Boolean.FALSE, Boolean.TRUE);
        }
        return false;
    }

    /** Removes the pending marker and returns whether the request was forced. */
    boolean endResync(UUID uuid) {
        return Boolean.TRUE.equals(pendingResyncs.remove(uuid));
    }

    int trackedPlayers() {
        return playerIdsByUuid.size();
    }

    int cachedEntities() {
        return lastSentHand.size();
    }

    void clear() {
        blockedHits.clear();
        lastSentHand.clear();
        playerIdsByUuid.clear();
        playerEntityIds.clear();
        pendingResyncs.clear();
    }

    private static int quantize(double coordinate) {
        // ClientboundSoundPacket stores x/y/z as fixed-point eighths of a block.
        return (int) (coordinate * 8.0D);
    }
}
