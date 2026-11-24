package org.pvpcore.mechanic.totemfixes;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks inventory actions observed on PacketEvents' network thread and their matching
 * Bukkit events on the server thread.
 *
 * <p>No Bukkit objects are accessed from this class. Packet receipt and event processing
 * may happen on different threads, so counters are atomic and timestamps are volatile.</p>
 */
final class TotemSwapTracker {

    enum Action {
        INVENTORY_CLICK,
        HAND_SWAP,
        HOTBAR_CHANGE,
        CREATIVE_ACTION
    }

    private static final class ActionCounter {
        private final AtomicLong received = new AtomicLong();
        private final AtomicLong processed = new AtomicLong();
        private volatile long lastReceivedNanos;

        void packetReceived(long nowNanos) {
            // Publish the timestamp before incrementing the visible received count.
            lastReceivedNanos = nowNanos;
            received.incrementAndGet();
        }

        void eventProcessed() {
            // A Bukkit event acknowledges one packet of this action type. Do not let
            // plugin-fired/synthetic Bukkit events make the processed count exceed the
            // number of packets actually seen from the client.
            while (true) {
                long seen = received.get();
                long done = processed.get();
                if (done >= seen) {
                    return;
                }
                if (processed.compareAndSet(done, done + 1)) {
                    return;
                }
            }
        }

        boolean hasRecentUnprocessedPacket(long nowNanos, long maxAgeNanos) {
            if (received.get() <= processed.get()) {
                return false;
            }
            long age = nowNanos - lastReceivedNanos;
            return age >= 0L && age <= maxAgeNanos;
        }
    }

    private static final class PlayerState {
        private final Map<Action, ActionCounter> counters = new EnumMap<>(Action.class);

        private PlayerState() {
            for (Action action : Action.values()) {
                counters.put(action, new ActionCounter());
            }
        }

        ActionCounter counter(Action action) {
            return counters.get(action);
        }

        boolean hasRecentPending(long nowNanos, long maxAgeNanos) {
            for (ActionCounter counter : counters.values()) {
                if (counter.hasRecentUnprocessedPacket(nowNanos, maxAgeNanos)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final ConcurrentMap<UUID, PlayerState> players = new ConcurrentHashMap<>();

    void packetReceived(UUID playerId, Action action) {
        if (playerId == null || action == null) {
            return;
        }
        players.computeIfAbsent(playerId, ignored -> new PlayerState())
                .counter(action)
                .packetReceived(System.nanoTime());
    }

    void eventProcessed(UUID playerId, Action action) {
        if (playerId == null || action == null) {
            return;
        }
        PlayerState state = players.get(playerId);
        if (state != null) {
            state.counter(action).eventProcessed();
        }
    }

    boolean hasRecentUnprocessedAction(UUID playerId, long nowNanos, long maxAgeNanos) {
        PlayerState state = players.get(playerId);
        return state != null && state.hasRecentPending(nowNanos, maxAgeNanos);
    }

    void remove(UUID playerId) {
        if (playerId != null) {
            players.remove(playerId);
        }
    }

    void clear() {
        players.clear();
    }
}
