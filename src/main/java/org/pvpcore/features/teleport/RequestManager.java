package org.pvpcore.features.teleport;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pending /tpa and /tpahere requests. One outgoing request per sender: a new one replaces the
 * old one. Expiry is checked lazily, so there is no repeating task.
 *
 * <p>Main thread only, like the rest of the Bukkit API. No locking.</p>
 */
public final class RequestManager {

    public enum Type {
        /** /tpa: the sender wants to go to the target. */
        TO_TARGET,
        /** /tpahere: the sender wants the target to come to them. */
        TARGET_HERE
    }

    public record Request(UUID sender, UUID target, Type type, long createdAt, long expiresAt) {
        public boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private final Map<UUID, Request> bySender = new HashMap<>();
    private final Map<UUID, Long> lastSent = new HashMap<>();

    /** Milliseconds left on the sender's post-request cooldown, or 0 if none. */
    public long cooldownRemaining(UUID sender, long now, long cooldownMillis) {
        if (cooldownMillis <= 0) {
            return 0;
        }
        Long until = lastSent.get(sender);
        if (until == null) {
            return 0;
        }
        long elapsed = now - until;
        if (elapsed >= cooldownMillis) {
            lastSent.remove(sender);
            return 0;
        }
        return cooldownMillis - elapsed;
    }

    /** Adds or replaces the sender's pending request. */
    public Request submit(UUID sender, UUID target, Type type, long now, long expiryMillis) {
        Request request = new Request(sender, target, type, now, now + expiryMillis);
        bySender.put(sender, request);
        lastSent.put(sender, now);
        return request;
    }

    /** Returns the sender's pending request if it is still live. */
    public Optional<Request> pendingFrom(UUID sender, long now) {
        Request request = bySender.get(sender);
        if (request == null || request.isExpired(now)) {
            return Optional.empty();
        }
        return Optional.of(request);
    }

    /**
     * Removes and returns the newest live request aimed at {@code target}. Used by
     * /tpaccept and /tpdeny. Returns empty when nothing is pending or everything has expired.
     */
    public Optional<Request> takeFor(UUID target, long now) {
        pruneExpired(now);
        Request newest = null;
        for (Request request : bySender.values()) {
            if (request.target().equals(target)
                    && (newest == null || request.createdAt() > newest.createdAt())) {
                newest = request;
            }
        }
        if (newest != null) {
            bySender.remove(newest.sender());
        }
        return Optional.ofNullable(newest);
    }

    /** Cancels the sender's own request. Returns true if one was removed. */
    public boolean cancelFrom(UUID sender) {
        return bySender.remove(sender) != null;
    }

    /** Drops requests a player sent or received. Call on quit. */
    public void removeInvolving(UUID player) {
        bySender.remove(player);
        bySender.values().removeIf(request -> request.target().equals(player));
        lastSent.remove(player);
    }

    public void pruneExpired(long now) {
        bySender.values().removeIf(request -> request.isExpired(now));
    }

    public void clear() {
        bySender.clear();
        lastSent.clear();
    }
}