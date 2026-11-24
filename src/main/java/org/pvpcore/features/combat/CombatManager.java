package org.pvpcore.features.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the live combat state for every player.
 *
 * <p>Other features must consume this class through its public API; the internal session map is
 * never exposed. Sessions are in-memory only, expire after {@code tag-duration-seconds} in
 * {@code combat/config.yml}, and are not persisted across restarts.</p>
 *
 * <p>Expiry is evaluated lazily on lookup and in one shared cleanup pass
 * ({@link #cleanupExpired()}), which the feature's single scheduler task calls periodically.
 * No per-player timers exist.</p>
 *
 * <p>All methods must be called from the server thread. Calling them from another thread throws
 * {@link IllegalStateException} (the check is skipped when no server is running, e.g. in tests).</p>
 */
public final class CombatManager {

    private CombatConfig config; // main-thread only; replaced by reload()
    private final Map<UUID, CombatSession> sessions = new HashMap<>();

    public CombatManager(CombatConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    /** True when combat tagging is enabled in {@code combat/config.yml}. Live config. */
    public boolean isEnabled() {
        return config.enabled();
    }

    /** Package-private config access for feature wiring and listeners. */
    CombatConfig config() {
        return config;
    }

    /** Swaps in a new validated config (e.g. after {@code /pvp-core reload}). Main thread only. */
    public void reload(CombatConfig newConfig) {
        checkServerThread();
        if (newConfig != null) {
            this.config = newConfig;
        }
    }

    /** True while the player has a live (non-expired) combat session. False when the module is disabled. */
    public boolean isInCombat(UUID playerId) {
        checkServerThread();
        if (!config.enabled()) {
            return false;
        }
        return getSession(playerId).isPresent();
    }

    /** The player's live session, if any. An expired session is removed and reported as absent. Empty when disabled. */
    public Optional<CombatSession> getSession(UUID playerId) {
        checkServerThread();
        if (playerId == null || !config.enabled()) {
            return Optional.empty();
        }
        CombatSession session = sessions.get(playerId);
        if (session == null) {
            return Optional.empty();
        }
        if (isExpired(session, System.nanoTime())) {
            sessions.remove(playerId, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Tags both players as opponents of each other without any damage (e.g. combat-zone radius
     * proximity tagging). Overwrites both players' opponent to point at each other and refreshes
     * both timers.
     */
    public void tagBoth(UUID playerId, UUID opponentId, CombatCause cause) {
        checkServerThread();
        if (playerId == null || opponentId == null || playerId.equals(opponentId)) {
            return;
        }
        if (!config.enabled()) {
            return;
        }
        long expiresAtNanos = saturatedAdd(System.nanoTime(), config.tagDurationNanos());
        sessions.put(playerId, new CombatSession(playerId, opponentId, cause, expiresAtNanos));
        sessions.put(opponentId, new CombatSession(opponentId, playerId, cause, expiresAtNanos));
    }

    /**
     * Tags a single player as combat-tagged without a known opponent (e.g. an unattributed
     * explosion or a pearl landing at nobody's feet). Refreshes the session if one exists, and
     * keeps any previously recorded opponent.
     *
     * <p>Player-scoped: the player must be the one being tagged. There is no cross-world
     * consideration - the caller decides which players are in range.</p>
     */
    public void tagPlayer(UUID playerId, CombatCause cause) {
        checkServerThread();
        if (playerId == null || !config.enabled()) {
            return;
        }
        UUID opponent = getSession(playerId).map(CombatSession::opponentId).orElse(null);
        long expiresAtNanos = saturatedAdd(System.nanoTime(), config.tagDurationNanos());
        sessions.put(playerId, new CombatSession(playerId, opponent, cause, expiresAtNanos));
    }

    /**
     * Tags both parties of a confidently player-caused hit. Non-player-caused contexts, contexts
     * without an attacker, and self-damage are ignored. Creates or refreshes both sessions and
     * records the opponent and cause on each.
     */
    public void recordPlayerCombat(CombatContext context) {
        checkServerThread();
        if (!config.enabled()) {
            return; // module disabled: never tag
        }
        if (context == null || !context.isPlayerCaused()) {
            return;
        }
        UUID attackerId = context.attackerId();
        UUID victimId = context.victimId();
        if (attackerId.equals(victimId)) {
            return;
        }
        long expiresAtNanos = saturatedAdd(System.nanoTime(), config.tagDurationNanos());
        sessions.put(attackerId, new CombatSession(attackerId, victimId, context.cause(), expiresAtNanos));
        sessions.put(victimId, new CombatSession(victimId, attackerId, context.cause(), expiresAtNanos));
    }

    /**
     * Removes the player's session, if present.
     *
     * <p>Note this clears only the named player's session. On death the victim is cleared and the
     * attacker remains tagged until their own timer expires - see {@code CombatListener#onDeath};
     * on quit and on {@code /pvp-core} disable only the named player (or everyone) is cleared.</p>
     *
     * <p>This does not fire a feature event; PvPCore has no custom event types, so removal is a
     * plain map operation. Integrations that need to react before a session disappears (e.g. a
     * logout penalty policy) must inspect {@link #getSession(UUID)} first - see
     * {@code CombatListener#onQuit(PlayerQuitEvent)} for the wiring. {@code reason} is currently
     * informational only and is retained so integrations and future events can distinguish
     * {@link CombatEndReason#EXPIRED}, {@link CombatEndReason#DEATH}, {@link CombatEndReason#LOGOUT},
     * {@link CombatEndReason#CLEARED} and {@link CombatEndReason#PLUGIN_DISABLE}.</p>
     *
     * @return true when a session was present and removed
     */
    public boolean clear(UUID playerId, CombatEndReason reason) {
        checkServerThread();
        if (playerId == null) {
            return false;
        }
        return sessions.remove(playerId) != null;
    }

    /** Clears every transient session. Used on feature shutdown. */
    public void clearAll(CombatEndReason reason) {
        checkServerThread();
        sessions.clear();
    }

    /** Removes all expired sessions. Called by the feature's single shared cleanup task. */
    public void cleanupExpired() {
        checkServerThread();
        long now = System.nanoTime();
        sessions.values().removeIf(session -> isExpired(session, now));
    }

    /**
     * Throws when called off the server thread. The check is skipped when no server exists
     * (offline unit tests), so the manager stays usable outside a running server.
     */
    static void checkServerThread() {
        if (org.bukkit.Bukkit.getServer() != null && !org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "Combat tag API must be used from the server thread, not " + Thread.currentThread().getName());
        }
    }

    /** {@code now + duration}, saturated at {@link Long#MAX_VALUE} instead of wrapping negative. */
    static long saturatedAdd(long nowNanos, long durationNanos) {
        long sum = nowNanos + durationNanos;
        if (durationNanos > 0 && sum < nowNanos) {
            return Long.MAX_VALUE;
        }
        return sum;
    }

    private static boolean isExpired(CombatSession session, long nowNanos) {
        // Same monotonic clock on both sides; nanoTime wraps roughly every 292 years.
        return session.expiresAtNanos() - nowNanos <= 0L;
    }
}
