package org.pvpcore.features.teleport.rtp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * In-memory 1v1 RTP matchmaking queue. Main-thread only: no locking and no cross-thread access.
 * One FIFO per region (case-insensitive id), exactly two players per match, paired synchronously
 * inside {@link #join(String, UUID)}; the sink runs only after state has been fully updated.
 */
public final class RtpQueue {
    public enum JoinResult { JOINED, ALREADY_IN_REGION, ALREADY_IN_OTHER_REGION }

    private final Map<String, Deque<UUID>> queues = new LinkedHashMap<>();
    private final Map<UUID, String> regionByPlayer = new HashMap<>();
    private Consumer<RtpMatch> matchSink = match -> { };

    public void setMatchSink(Consumer<RtpMatch> sink) {
        this.matchSink = sink == null ? match -> { } : sink;
    }

    public JoinResult join(String regionId, UUID player) {
        if (regionId == null || player == null) {
            throw new IllegalArgumentException("regionId and player must be non-null");
        }
        String existing = regionByPlayer.get(player);
        if (existing != null) {
            return existing.toLowerCase(Locale.ROOT).equals(regionId.toLowerCase(Locale.ROOT))
                    ? JoinResult.ALREADY_IN_REGION : JoinResult.ALREADY_IN_OTHER_REGION;
        }

        Deque<UUID> regionQueue = queues.computeIfAbsent(regionId.toLowerCase(Locale.ROOT),
                key -> new ArrayDeque<>());
        regionQueue.addLast(player);
        regionByPlayer.put(player, regionId);

        if (regionQueue.size() >= 2) {
            UUID first = regionQueue.removeFirst();
            UUID second = regionQueue.removeFirst();
            regionByPlayer.remove(first);
            regionByPlayer.remove(second);
            if (regionQueue.isEmpty()) {
                queues.remove(regionId.toLowerCase(Locale.ROOT));
            }
            matchSink.accept(new RtpMatch(regionId, first, second));
        }
        return JoinResult.JOINED;
    }

    public boolean leave(UUID player) {
        String regionId = regionByPlayer.remove(player);
        if (regionId == null) {
            return false;
        }
        String key = regionId.toLowerCase(Locale.ROOT);
        Deque<UUID> regionQueue = queues.get(key);
        regionQueue.remove(player);
        if (regionQueue.isEmpty()) {
            queues.remove(key);
        }
        return true;
    }

    public boolean isQueued(UUID player) {
        return regionByPlayer.containsKey(player);
    }

    public Optional<String> queuedRegion(UUID player) {
        return Optional.ofNullable(regionByPlayer.get(player));
    }

    public int size(String regionId) {
        Deque<UUID> regionQueue = queues.get(regionId.toLowerCase(Locale.ROOT));
        return regionQueue == null ? 0 : regionQueue.size();
    }

    public int size() {
        return regionByPlayer.size();
    }

    public void clear() {
        queues.clear();
        regionByPlayer.clear();
    }
}
