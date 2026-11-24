package org.pvpcore.features.combat;

import java.util.UUID;

/**
 * One player's live combat state.
 *
 * <p>Sessions are time-based and in-memory only; they are not persisted and do not survive a
 * server restart. {@code expiresAtNanos} is a {@code System.nanoTime()} value and must be
 * compared against the same clock source.</p>
 *
 * @param playerId       the tagged player
 * @param opponentId     the other party in the last qualifying hit, or {@code null} for a session
 *                       created without a known opponent (e.g. by a future integration API)
 * @param lastCause      cause of the last qualifying hit that created or refreshed this session
 * @param expiresAtNanos {@code System.nanoTime()} value at which this session expires
 */
public record CombatSession(
        UUID playerId,
        UUID opponentId,          // may be null for a session without a known opponent
        CombatCause lastCause,
        long expiresAtNanos
) {}
