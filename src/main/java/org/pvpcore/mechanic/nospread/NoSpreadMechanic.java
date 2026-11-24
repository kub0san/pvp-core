package org.pvpcore.mechanic.nospread;

import org.bukkit.entity.EntityType;
import org.bukkit.event.HandlerList;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.util.ProjectileFilter;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Removes the random spread from projectiles fired by players.
 * <p>
 * The set of projectiles that are affected can be narrowed with a blacklist (default)
 * or a whitelist. The filter is re-read on {@code /pvp-core reload}; newly launched
 * projectiles use the new filter, while in-flight ones are untouched.
 */
public final class NoSpreadMechanic implements Mechanic {

    private static final String MODE_KEY = "mechanics.nospread.mode";
    private static final String ENTITIES_KEY = "mechanics.nospread.entities";
    private static final String MODE_BLACKLIST = "blacklist";
    private static final String MODE_WHITELIST = "whitelist";

    private PvPCorePlugin plugin;
    private boolean debug;
    private boolean enabled;
    private ProjectileLaunchListener listener;

    /** Current filter; volatile so the listener sees a reload consistently. */
    private volatile ProjectileFilter filter = ProjectileFilter.ALL;

    @Override
    public String id() {
        return "nospread";
    }

    @Override
    public String displayName() {
        return "No Projectile Spread";
    }

    @Override
    public String description() {
        return "Removes the random spread from player projectiles: every shot leaves along the crosshair.";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    boolean isDebug() {
        return debug;
    }

    /** True when the current filter accepts this entity type. Safe from any thread. */
    boolean accepts(EntityType type) {
        return filter.accepts(type);
    }

    /** Verbose logging, enabled by the root {@code debug} config flag. */
    void debug(String message) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    /** Logs an unexpected failure once, then only counts it. */
    void logFailure(String what, Throwable t) {
        if (plugin != null) {
            plugin.getLogger().log(Level.WARNING, "Projectile spread could not be removed: " + what, t);
        }
    }

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        if (enabled) {
            return true;
        }
        this.plugin = plugin;
        this.debug = CoreConfig.isDebug(plugin);

        this.filter = loadFilter(plugin);
        listener = new ProjectileLaunchListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        enabled = true;

        plugin.getLogger().info("No projectile spread enabled: player shots leave exactly along "
                + "the crosshair.");
        plugin.getLogger().info("No projectile spread filter: " + filter.describe() + ".");
        if (filter.isEmptyWhitelist()) {
            plugin.getLogger().warning("No projectile spread is set to whitelist mode with an empty "
                    + "list; no projectile will be affected. Add entity types to " + ENTITIES_KEY
                    + " or switch the mode to blacklist.");
        }
        debug("Snap tolerance " + AimDirection.SNAP_TOLERANCE_DEGREES + " degrees; Multishot "
                + "crossbows keep their " + AimDirection.MULTISHOT_DEGREES_PER_LEVEL
                + " degree per level offsets.");
        return true;
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!enabled) {
            return;
        }
        enabled = false;

        if (listener != null) {
            HandlerList.unregisterAll(listener);
            debug("Disabled. " + listener.summary() + ".");
            listener = null;
        }
    }

    @Override
    public void refresh(PvPCorePlugin plugin) {
        boolean wasDebug = debug;
        debug = CoreConfig.isDebug(plugin);
        if (wasDebug != debug) {
            plugin.getLogger().info("No projectile spread debug logging "
                    + (debug ? "enabled" : "disabled") + ".");
        }

        ProjectileFilter previous = filter;
        ProjectileFilter updated = loadFilter(plugin);
        filter = updated;

        if (!previous.sameAs(updated)) {
            plugin.getLogger().info("No projectile spread filter changed: " + updated.describe()
                    + ". Newly launched projectiles use the new filter.");
            if (updated.isEmptyWhitelist()) {
                plugin.getLogger().warning("No projectile spread is set to whitelist mode with an "
                        + "empty list; no new projectile will be affected.");
            }
        }

        if (debug && listener != null) {
            debug("State: " + listener.summary() + "; filter " + filter.describe() + ".");
        }
    }

    // ------------------------------------------------------------------
    // Filter configuration
    // ------------------------------------------------------------------

    private ProjectileFilter loadFilter(PvPCorePlugin plugin) {
        String modeRaw = plugin.getConfig().getString(MODE_KEY, MODE_BLACKLIST);
        String mode = modeRaw == null ? "" : modeRaw.trim().toLowerCase(Locale.ROOT);
        boolean whitelist;
        if (MODE_WHITELIST.equals(mode)) {
            whitelist = true;
        } else if (MODE_BLACKLIST.equals(mode)) {
            whitelist = false;
        } else {
            plugin.getLogger().warning("Unknown mode '" + modeRaw + "' for " + MODE_KEY
                    + "; falling back to " + MODE_BLACKLIST + ".");
            whitelist = false;
        }

        List<String> names = plugin.getConfig().getStringList(ENTITIES_KEY);
        if (names.isEmpty()) {
            names = List.of("ARROW", "SPECTRAL_ARROW");
        }
        EnumSet<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (String raw : names) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            EntityType type;
            try {
                type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown entity type '" + name + "' in "
                        + ENTITIES_KEY + "; skipping.");
                continue;
            }

            // Check if this type is a projectile before adding
            ProjectileFilter probe = new ProjectileFilter(whitelist, EnumSet.of(type));
            if (probe.containsNonProjectile(type)) {
                plugin.getLogger().warning("'" + name + "' is not a projectile entity type "
                        + "and will never match. Skipping.");
                continue;
            }
            types.add(type);
        }

        return new ProjectileFilter(whitelist, types);
    }
}