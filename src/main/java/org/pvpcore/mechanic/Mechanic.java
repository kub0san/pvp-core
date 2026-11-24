package org.pvpcore.mechanic;

import org.pvpcore.PvPCorePlugin;

import java.util.Set;

/**
 * A self-contained PvP mechanic.
 *
 * <p>Implementations must be cheap when disabled: no listeners registered, no tasks
 * scheduled and no per-player state. {@link #enable(PvPCorePlugin)} must only change
 * things it can undo, and {@link #disable(PvPCorePlugin)} must restore the server to
 * stock Paper behaviour.</p>
 *
 * <p>All methods are called from the server main thread. Implementations must be
 * idempotent and must never throw; return {@code false} or log instead.</p>
 */
public interface Mechanic {

    /** Stable id used by the command and config, for example {@code attributeswap}. */
    String id();

    /**
     * Alternative ids that also resolve to this mechanic, for example an id that was
     * renamed later. Aliases are accepted by {@code /pvp-core} and by tab completion.
     * The primary id from {@link #id()} is the one that is written to the config.
     *
     * @return the alternative ids, never {@code null}
     */
    default Set<String> aliases() {
        return Set.of();
    }

    /** Human readable name shown by {@code /pvp-core list}. */
    String displayName();

    /** One line description shown by {@code /pvp-core list}. */
    String description();

    /** State used when the config has no value for this mechanic yet. */
    boolean defaultEnabled();

    /**
     * Resolves reflection, applies server-wide changes and registers listeners.
     *
     * @return {@code true} when the mechanic is fully or partially active
     */
    boolean enable(PvPCorePlugin plugin);

    /** Undoes everything {@link #enable(PvPCorePlugin)} did. Must be idempotent. */
    void disable(PvPCorePlugin plugin);

    boolean isEnabled();

    /**
     * Optional short note shown next to the mechanic in {@code /pvp-core list}, for
     * example when the server already provides the behaviour for another reason.
     *
     * @return the note, or {@code null}
     */
    default String statusNote() {
        return null;
    }

    /**
     * Observable status with reason. Defaults to READY when enabled, otherwise DISABLED_BY_CONFIG.
     * Registry/implementations may override for more specific reasons.
     */
    default MechanicStatus status() {
        if (isEnabled()) {
            String note = statusNote();
            return MechanicStatus.ready(note == null || note.isBlank() ? "Enabled" : note);
        }
        String note = statusNote();
        return MechanicStatus.disabledByConfig(note == null || note.isBlank() ? "Disabled by config" : note);
    }

    /** Declared optional/runtime dependencies that must be present for this mechanic to enable. */
    default java.util.Set<String> requiredPlugins() {
        return java.util.Set.of();
    }

    /** Declared PacketEvents requirement (>= supported gate). */
    default boolean requiresPacketEvents() {
        return false;
    }

    /**
     * Declared supported server version range; null means no explicit restriction.
     * Format: {@code lower..upper} (inclusive), e.g. {@code 1.21.11..26.3}; either bound may
     * be omitted. A single version is treated as a minimum.
     */
    default String supportedVersionRange() {
        return null;
    }

    /**
     * Called on {@code /pvp-core reload} for a mechanic that was already enabled and
     * stays enabled. Use it to re-apply server-wide settings after a config reload;
     * the default does nothing.
     */
    default void refresh(PvPCorePlugin plugin) {
    }
}
