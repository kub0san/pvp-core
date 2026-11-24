package org.pvpcore.mechanic;

import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.util.VersionSupport;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Holds every known mechanic and is the only place that toggles them.
 *
 * <p>Registration order is stable so {@code /pvp-core list} and tab completion keep a
 * predictable order. Failures are contained here: one broken mechanic must never break
 * plugin startup, another mechanic, or shutdown.</p>
 */
public final class MechanicRegistry {

    public enum Status {
        APPLIED,
        UNCHANGED,
        UNKNOWN,
        FAILED
    }

    /** Outcome of a toggle, including a message the command can show. */
    public record Result(Status status, String message) {
        public boolean success() {
            return status == Status.APPLIED || status == Status.UNCHANGED;
        }
    }

    private final PvPCorePlugin plugin;
    private final CoreConfig config;
    private final Map<String, Mechanic> mechanics = new LinkedHashMap<>();
    /** Alias id to the primary id of the mechanic it belongs to. */
    private final Map<String, String> aliases = new HashMap<>();
    private final Map<String, MechanicStatus> statuses = new HashMap<>();

    public MechanicRegistry(PvPCorePlugin plugin, CoreConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Registers a mechanic atomically. All ids are validated before either registry map is
     * mutated, so a rejected alias cannot leave a partially registered mechanic behind.
     *
     * @throws IllegalArgumentException when the mechanic or one of its ids is invalid
     * @throws IllegalStateException when an id or alias is already registered
     */
    public void register(Mechanic mechanic) {
        if (mechanic == null) {
            throw new IllegalArgumentException("Mechanic cannot be null");
        }

        String id = normalizeId(mechanic.id(), "mechanic id");
        if (mechanics.containsKey(id) || aliases.containsKey(id)) {
            throw new IllegalStateException("Duplicate mechanic id or alias: " + id);
        }

        Set<String> normalizedAliases = new HashSet<>();
        Set<String> declaredAliases = mechanic.aliases();
        if (declaredAliases == null) {
            throw new IllegalArgumentException("Mechanic '" + id + "' returned null aliases");
        }

        for (String alias : declaredAliases) {
            String key = normalizeId(alias, "alias for " + id);
            if (key.equals(id)) {
                throw new IllegalArgumentException("Mechanic '" + id + "' lists its own id as an alias");
            }
            if (!normalizedAliases.add(key)) {
                throw new IllegalStateException("Duplicate alias on mechanic '" + id + "': " + key);
            }
            if (mechanics.containsKey(key) || aliases.containsKey(key)) {
                throw new IllegalStateException("Duplicate mechanic id or alias: " + key);
            }
        }

        mechanics.put(id, mechanic);
        for (String alias : normalizedAliases) {
            aliases.put(alias, id);
        }
        statuses.put(id, deriveStatus(mechanic));
    }

    private static String normalizeId(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(description + " cannot be null or blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** Resolves a mechanic by its id or by one of its aliases. */
    public Mechanic get(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        Mechanic mechanic = mechanics.get(key);
        if (mechanic != null) {
            return mechanic;
        }
        String primary = aliases.get(key);
        return primary == null ? null : mechanics.get(primary);
    }

    public Collection<Mechanic> all() {
        return Collections.unmodifiableCollection(mechanics.values());
    }

    public MechanicStatus status(String id) {
        Mechanic mechanic = get(id);
        if (mechanic == null) return null;
        return status(mechanic);
    }

    public MechanicStatus status(Mechanic mechanic) {
        MechanicStatus tracked = statuses.get(mechanic.id());
        MechanicStatus current = deriveStatus(mechanic);
        if (tracked == null) return current;
        // Prefer specific tracked reasons for inactive/failure states; fall back to current derived state.
        if (mechanic.isEnabled()) {
            return current.state() == MechanicStatus.State.READY ? current : MechanicStatus.ready(current.reason());
        }
        if (tracked.state() == MechanicStatus.State.MISSING_DEPENDENCY || tracked.state() == MechanicStatus.State.UNSUPPORTED_VERSION) {
            return tracked;
        }
        return current;
    }

    private MechanicStatus deriveStatus(Mechanic mechanic) {
        try {
            MechanicStatus s = mechanic.status();
            if (s != null) return s;
        } catch (Throwable ignored) {
        }
        if (mechanic.isEnabled()) {
            String note = mechanic.statusNote();
            return MechanicStatus.ready(note == null || note.isBlank() ? "Enabled" : note);
        }
        String note = mechanic.statusNote();
        if (note != null && !note.isBlank()) {
            String lower = note.toLowerCase(Locale.ROOT);
            if (lower.contains("not installed") || lower.contains("missing") || (lower.contains("packetevents") && !lower.contains("running"))) {
                return MechanicStatus.missingDependency(note);
            }
            if (lower.contains("unsupported") || lower.contains("below the supported version")) {
                return MechanicStatus.unsupportedVersion(note);
            }
        }
        return MechanicStatus.disabledByConfig(note == null || note.isBlank() ? "Disabled by config" : note);
    }

    private void updateStatus(Mechanic mechanic, MechanicStatus status) {
        statuses.put(mechanic.id(), status != null ? status : deriveStatus(mechanic));
    }

    /** Enables or disables a mechanic and persists the requested state only after success. */
    public Result set(String id, boolean enable) {
        Mechanic mechanic = get(id);
        if (mechanic == null) {
            return new Result(Status.UNKNOWN, "Unknown mechanic: " + id);
        }

        if (mechanic.isEnabled() == enable) {
            config.setEnabled(mechanic.id(), enable);
            updateStatus(mechanic, enable ? deriveStatus(mechanic) : MechanicStatus.disabledByConfig("Disabled by config"));
            return new Result(Status.UNCHANGED,
                    mechanic.displayName() + " is already " + (enable ? "ON" : "OFF") + ".");
        }

        if (enable) {
            if (!enableSafely(mechanic, "being enabled", true)) {
                return new Result(Status.FAILED,
                        "Could not enable " + mechanic.displayName() + ", see console.");
            }
        } else {
            try {
                mechanic.disable(plugin);
                if (mechanic.isEnabled()) {
                    plugin.getLogger().warning("Mechanic '" + mechanic.id()
                            + "' returned from disable() but still reports enabled; retrying cleanup.");
                    bestEffortDisable(mechanic);
                    if (mechanic.isEnabled()) {
                        return new Result(Status.FAILED,
                                "Could not disable " + mechanic.displayName() + ", see console.");
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE,
                        "Mechanic '" + mechanic.id() + "' threw while disabling", t);
                // The contract requires disable() to be idempotent. A second attempt gives
                // implementations a chance to finish cleanup after a transient failure.
                bestEffortDisable(mechanic);
                return new Result(Status.FAILED,
                        "Mechanic " + mechanic.displayName() + " failed, see console.");
            }
            updateStatus(mechanic, MechanicStatus.disabledByConfig("Disabled by config"));
        }

        config.setEnabled(mechanic.id(), enable);
        return new Result(Status.APPLIED,
                mechanic.displayName() + " is now " + (enable ? "ON" : "OFF") + ".");
    }

    public Result toggle(String id) {
        Mechanic mechanic = get(id);
        if (mechanic == null) {
            return new Result(Status.UNKNOWN, "Unknown mechanic: " + id);
        }
        return set(mechanic.id(), !mechanic.isEnabled());
    }

    /** Enables every mechanic whose persisted state says it should be on. */
    public void enableConfigured() {
        for (Mechanic mechanic : mechanics.values()) {
            boolean desired = config.isEnabled(mechanic.id(), mechanic.defaultEnabled());
            if (!desired) {
                if (!mechanic.isEnabled()) {
                    updateStatus(mechanic, MechanicStatus.disabledByConfig("Disabled by config"));
                }
                continue;
            }
            if (mechanic.isEnabled()) {
                updateStatus(mechanic, deriveStatus(mechanic));
                continue;
            }
            enableSafely(mechanic, "being started from config", false);
        }
    }

    /**
     * Attempts activation and always invokes disable() after a refused, inconsistent, or
     * exceptional start. This covers implementations that registered a listener or task
     * before discovering that they cannot fully activate.
     */
    private boolean enableSafely(Mechanic mechanic, String context, boolean requestedByToggle) {
        MechanicStatus requirementFailure = checkRequirements(mechanic);
        if (requirementFailure != null) {
            plugin.getLogger().warning("Mechanic '" + mechanic.id() + "' cannot enable: " + requirementFailure.reason());
            bestEffortDisable(mechanic);
            updateStatus(mechanic, requirementFailure);
            return false;
        }
        try {
            boolean started = mechanic.enable(plugin);
            if (started && mechanic.isEnabled()) {
                updateStatus(mechanic, deriveStatus(mechanic));
                return true;
            }
            String reason = started
                    ? "returned true but reports disabled"
                    : "returned false";
            plugin.getLogger().warning("Mechanic '" + mechanic.id() + "' " + reason + " while " + context + ".");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE,
                    "Mechanic '" + mechanic.id() + "' threw while " + context, t);
        }

        bestEffortDisable(mechanic);
        updateStatus(mechanic, failureStatus(mechanic, requestedByToggle));
        return false;
    }

    private MechanicStatus checkRequirements(Mechanic mechanic) {
        if (mechanic.requiresPacketEvents() && !PacketEventsGate.ready()) {
            return MechanicStatus.missingDependency(PacketEventsGate.unavailableDetail());
        }
        for (String pluginName : mechanic.requiredPlugins()) {
            org.bukkit.plugin.Plugin dep = plugin.getServer().getPluginManager().getPlugin(pluginName);
            if (dep == null) {
                return MechanicStatus.missingDependency(pluginName + " is not installed");
            }
            // Installed but disabled: let the mechanic decide (e.g. late-start/waiting hooks).
        }
        String range = mechanic.supportedVersionRange();
        if (range != null && !range.isBlank() && !isVersionInDeclaredRange(range)) {
            return MechanicStatus.unsupportedVersion("Unsupported server version for range " + range);
        }
        return null;
    }

    private boolean isVersionInDeclaredRange(String range) {
        return isVersionInDeclaredRange(range, VersionSupport.minecraftVersion());
    }

    /**
     * Checks a declared range of the form {@code lower..upper} (inclusive), with either bound
     * optional ({@code ..26.2}, {@code 1.21.11..}) and a single version treated as a minimum.
     * Package-private so the pure comparison can be tested without a running server.
     */
    static boolean isVersionInDeclaredRange(String range, String currentVersion) {
        if (range == null || range.isBlank() || currentVersion == null) {
            return false;
        }
        int separator = range.indexOf("..");
        if (separator < 0) {
            return VersionSupport.compare(currentVersion, range.trim()) >= 0;
        }
        String lower = range.substring(0, separator).trim();
        String upper = range.substring(separator + 2).trim();
        if (!lower.isEmpty() && VersionSupport.compare(currentVersion, lower) < 0) {
            return false;
        }
        return upper.isEmpty() || VersionSupport.compare(currentVersion, upper) <= 0;
    }

    private MechanicStatus failureStatus(Mechanic mechanic, boolean requestedByToggle) {
        if (mechanic.requiresPacketEvents() && !PacketEventsGate.ready()) {
            return MechanicStatus.missingDependency(PacketEventsGate.unavailableDetail());
        }
        for (String pluginName : mechanic.requiredPlugins()) {
            org.bukkit.plugin.Plugin dep = plugin.getServer().getPluginManager().getPlugin(pluginName);
            if (dep == null) {
                return MechanicStatus.missingDependency(pluginName + " is not installed");
            }
        }
        String note = null;
        try {
            note = mechanic.statusNote();
        } catch (Throwable ignored) {
        }
        if (note != null && !note.isBlank()) {
            String lower = note.toLowerCase(Locale.ROOT);
            if (lower.contains("not installed") || lower.contains("missing") || lower.contains("packetevents")) {
                return MechanicStatus.missingDependency(note);
            }
            if (lower.contains("unsupported") || lower.contains("version")) {
                return MechanicStatus.unsupportedVersion(note);
            }
        }
        if (requestedByToggle) {
            return MechanicStatus.disabledByConfig(note == null || note.isBlank() ? "Failed to enable (see console)" : note);
        }
        return MechanicStatus.disabledByConfig(note == null || note.isBlank() ? "Disabled by config (failed to enable)" : note);
    }

    /** Cleanup is best-effort so one broken mechanic cannot stop other mechanics. */
    private void bestEffortDisable(Mechanic mechanic) {
        try {
            mechanic.disable(plugin);
            if (mechanic.isEnabled()) {
                plugin.getLogger().warning("Cleanup for mechanic '" + mechanic.id()
                        + "' returned but the mechanic still reports enabled.");
            }
        } catch (Throwable cleanupFailure) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not clean up mechanic '" + mechanic.id() + "'", cleanupFailure);
        }
    }

    /**
     * Re-reads the config and re-syncs every mechanic to the persisted state. Mechanics
     * whose state does not change are only refreshed, never restarted.
     */
    public void reload() {
        config.reload();
        for (Mechanic mechanic : mechanics.values()) {
            boolean desired = config.isEnabled(mechanic.id(), mechanic.defaultEnabled());
            if (mechanic.isEnabled() == desired) {
                if (desired) {
                    try {
                        mechanic.refresh(plugin);
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Mechanic '" + mechanic.id() + "' threw while refreshing", t);
                    }
                    updateStatus(mechanic, deriveStatus(mechanic));
                } else {
                    updateStatus(mechanic, MechanicStatus.disabledByConfig("Disabled by config"));
                }
                continue;
            }
            Result result = set(mechanic.id(), desired);
            if (!result.success()) {
                plugin.getLogger().warning(result.message());
            }
        }
    }

    /** Disables everything currently running; used on shutdown. */
    public void disableAll() {
        for (Mechanic mechanic : mechanics.values()) {
            if (!mechanic.isEnabled()) {
                continue;
            }
            try {
                mechanic.disable(plugin);
                if (mechanic.isEnabled()) {
                    plugin.getLogger().warning("Mechanic '" + mechanic.id()
                            + "' still reports enabled after shutdown cleanup.");
                }
            } catch (Throwable t) {
                // Continue: every other mechanic still has to be undone.
                plugin.getLogger().log(Level.SEVERE,
                        "Mechanic '" + mechanic.id() + "' threw while shutting down", t);
            }
        }
    }
}
