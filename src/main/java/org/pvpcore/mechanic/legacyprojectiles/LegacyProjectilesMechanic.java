package org.pvpcore.mechanic.legacyprojectiles;

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
 * Restores the projectile targeting tolerance of Minecraft 1.21.5 and older.
 *
 * <p>Minecraft 1.21.6 changed how projectiles target entities. The target tolerance margin
 * of projectiles now changes over time: a projectile starts with no margin for the first
 * ticks of its flight and the margin then grows by 0.05 blocks per tick until it reaches
 * the old margin of 0.3 blocks. Before 1.21.6 every projectile used the full 0.3 block
 * margin for its whole flight.</p>
 *
 * <p>Vanilla computes that margin from the age of the projectile
 * ({@code ProjectileUtil.computeMargin}), so restoring the old behaviour only requires
 * making a projectile look eight ticks old. The mechanic raises the age of every projectile
 * that is added to the world and lowers it again when the mechanic is disabled.</p>
 *
 * <p>This mechanic is intentionally non-vanilla. It is server side only: a client on 1.21.6
 * or newer still simulates the growing margin, so at close range the visual result and the
 * server result can disagree.</p>
 *
 * <p>The set of projectiles that are affected can be narrowed with a blacklist (default)
 * or a whitelist. Arrows ({@code ARROW} and {@code SPECTRAL_ARROW}) are blacklisted by
 * default. See {@link ProjectileFilter} for the modes. The filter is re-read on
 * {@code /pvp-core reload}; projectiles already in flight keep whatever age they had, and
 * only newly spawned projectiles use the new filter.</p>
 *
 * <p>Set {@code debug: true} in the config for verbose logging: every projectile that
 * enters the world, every age change, every filter exclusion, and a summary per reload
 * and disable.</p>
 */
public final class LegacyProjectilesMechanic implements Mechanic {

    /**
     * The age a projectile needs for the 1.21.6+ margin formula to return the full legacy
     * margin: {@code (8 - 2) / 20 = 0.3} blocks.
     */
    static final int LEGACY_AGE_TICKS = 8;

    /**
     * The id this mechanic had before it was renamed. It is still accepted by
     * {@code /pvp-core} so old commands and scripts keep working.
     */
    static final String FORMER_ID = "pre1216projectiles";

    private static final String MODE_KEY = "mechanics.legacyprojectiles.mode";
    private static final String ENTITIES_KEY = "mechanics.legacyprojectiles.entities";
    private static final String MODE_BLACKLIST = "blacklist";
    private static final String MODE_WHITELIST = "whitelist";
    /**
     * Default blacklist when the config has no {@code entities} list at all: arrows keep
     * vanilla behaviour because the mechanic is not an exact vanilla replica for them.
     */
    private static final List<String> DEFAULT_BLACKLIST = List.of("ARROW", "SPECTRAL_ARROW");

    /** Cached accessors, resolved on the first enable. */
    private static TickCountAccess access;

    private PvPCorePlugin plugin;
    private boolean debug;
    private boolean enabled;
    private ProjectileAgeListener listener;
    /** Current filter; volatile so the listener sees a reload consistently. */
    private volatile ProjectileFilter filter = ProjectileFilter.ALL;

    @Override
    public String id() {
        return "legacyprojectiles";
    }

    @Override
    public Set<String> aliases() {
        return Set.of(FORMER_ID);
    }

    @Override
    public String displayName() {
        return "Legacy Projectiles";
    }

    @Override
    public String description() {
        return "Approximate pre-1.21.6 projectile targeting tolerance: full 0.3 block margin from the first tick.";
    }

    @Override
    public String statusNote() {
        return "not an exact vanilla replica; arrows blacklisted by default";
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

    /** The plugin instance, for logging failures inside the listener. */
    PvPCorePlugin plugin() {
        return plugin;
    }

    /** Verbose logging, enabled by the root {@code debug} config flag. */
    void debug(String message) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        if (enabled) {
            return true;
        }

        this.plugin = plugin;
        this.debug = CoreConfig.isDebug(plugin);

        TickCountAccess resolved = access;
        if (resolved == null) {
            resolved = TickCountAccess.resolve();
            access = resolved;
        }
        if (resolved == null) {
            plugin.getLogger().severe("Legacy projectiles were not enabled: the entity age field "
                    + "could not be resolved on this server build. Nothing was changed.");
            return false;
        }

        this.filter = loadFilter(plugin);
        listener = new ProjectileAgeListener(this, resolved);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        enabled = true;

        plugin.getLogger().info("Legacy projectiles enabled: projectiles use the full 0.3 block "
                + "target margin from their first tick.");
        plugin.getLogger().info("Legacy projectiles filter: " + filter.describe() + ".");
        if (filter.isEmptyWhitelist()) {
            plugin.getLogger().warning("Legacy projectiles is set to whitelist mode with an empty "
                    + "list; no projectile will be affected. Add entity types to " + ENTITIES_KEY
                    + " or switch the mode to blacklist.");
        }
        debug("Age field resolved: " + TickCountAccess.FIELD_DESCRIPTION
                + "; legacy age " + LEGACY_AGE_TICKS + " ticks; vanilla margin formula "
                + "clamp((tickCount - 2) / 20, 0, 0.3).");
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
            int restored = listener.restoreOriginalAges();
            if (restored > 0) {
                plugin.getLogger().info("Legacy projectiles disabled: gave the legacy age back to "
                        + restored + " projectile(s) that were still in flight.");
            }
            debug("Disabled. " + listener.summary() + ".");
            listener = null;
        }
    }

    @Override
    public void refresh(PvPCorePlugin plugin) {
        boolean wasDebug = debug;
        debug = CoreConfig.isDebug(plugin);
        if (wasDebug != debug) {
            plugin.getLogger().info("Legacy projectiles debug logging "
                    + (debug ? "enabled" : "disabled") + ".");
        }

        ProjectileFilter previous = filter;
        ProjectileFilter updated = loadFilter(plugin);
        filter = updated;

        if (!previous.sameAs(updated)) {
            plugin.getLogger().info("Legacy projectiles filter changed: " + updated.describe()
                    + ". Newly spawned projectiles use the new filter; projectiles already "
                    + "in flight keep their current age until they leave the world.");
            if (updated.isEmptyWhitelist()) {
                plugin.getLogger().warning("Legacy projectiles is set to whitelist mode with an "
                        + "empty list; no new projectile will be affected.");
            }
        }

        if (debug && listener != null) {
            debug("State: " + listener.summary() + "; filter " + filter.describe() + ".");
        }
    }

    /** Logs an unexpected failure once, then only counts it. */
    void logFailure(String what, Throwable t) {
        plugin.getLogger().log(Level.WARNING, "Legacy projectiles could not " + what, t);
    }

    // ------------------------------------------------------------------
    // Filter configuration
    // ------------------------------------------------------------------

    /**
     * Reads {@code mechanics.legacyprojectiles.mode} and
     * {@code mechanics.legacyprojectiles.entities} from the live config.
     *
     * <p>Unknown entity type names are logged and skipped, and non-projectile types are
     * warned about because they can never match. A missing or unrecognised mode falls back
     * to {@code blacklist}. A missing {@code entities} list falls back to
     * {@link #DEFAULT_BLACKLIST} in blacklist mode, or to an empty list in whitelist mode.</p>
     */
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

        List<String> names;
        if (plugin.getConfig().isSet(ENTITIES_KEY)) {
            names = plugin.getConfig().getStringList(ENTITIES_KEY);
        } else {
            names = whitelist ? List.of() : DEFAULT_BLACKLIST;
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