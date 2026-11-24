package org.pvpcore.features.combat;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.module.PvPCoreModule;

import java.io.File;
import java.util.Set;
import java.util.UUID;

/**
 * Entry point for the Combat Tag feature, following the module pattern used elsewhere in this
 * plugin (see {@code features.teleport.TeleportModule}).
 *
 * <p>Lifecycle: {@link #enable()} builds the config, tracker, manager, resolver, listeners and
 * (if WorldGuard is installed) the safe-zone listener, and starts one shared synchronous cleanup
 * task; {@link #disable()} cancels it, unregisters the listeners, clears the transient anchor
 * records and clears all sessions. All Bukkit API access happens on the server thread.</p>
 *
 * <p>The logout penalty is a {@link CombatListener.LogoutPolicy} selected by
 * {@code combat/config.yml}'s {@code penalty.mode} ({@code none} / {@code drop} / {@code kill});
 * it defaults to {@code none}, so a punishing behavior is always an explicit admin choice.</p>
 */
public final class CombatTagFeature implements PvPCoreModule {

    /** Shared cleanup cadence: 1 second, comfortably below the shortest sensible session window. */
    private static final long CLEANUP_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;

    private CombatConfig config;
    private CombatMessages messages;
    private CombatManager combatManager;
    private AnchorAttributionTracker anchorTracker;
    private DamageAttributionResolver resolver;
    private CombatListener listener;
    private CombatPenaltyService penaltyService;
    private SafeZoneListener safeZoneListener;
    private BukkitTask cleanupTask;

    public CombatTagFeature(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null");
        }
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "combat";
    }

    @Override
    public Set<String> dependsOn() {
        return Set.of("kits");
    }

    @Override
    public void enable(PvPCorePlugin plugin) {
        enable();
    }

    /** Called from PvPCorePlugin#onEnable. Safe to call once; see {@link #disable()}. */
    public void enable() {
        try {
            this.messages = loadMessages();
            this.config = loadConfig();
            this.anchorTracker = new AnchorAttributionTracker(config);
            this.combatManager = new CombatManager(config);
            this.resolver = new BukkitDamageAttributionResolver(anchorTracker);
            this.penaltyService = new CombatPenaltyService(plugin, config, messages);

            // The policy reads the live config, so a penalty-mode change needs no re-wiring.
            this.listener = new CombatListener(combatManager, resolver, anchorTracker, penaltyService.policy());
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            plugin.getServer().getPluginManager().registerEvents(penaltyService, plugin);

            // WorldGuard is an optional dependency; the safe-zone listener never class-loads without it.
            if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null) {
                this.safeZoneListener = new SafeZoneListener(combatManager, messages);
                plugin.getServer().getPluginManager().registerEvents(safeZoneListener, plugin);
            }

            // One shared task for the whole feature; never one timer per player.
            this.cleanupTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, this::cleanupExpired, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);

            plugin.getLogger().info("combat: combat tag " + (config.enabled()
                    ? "enabled (tag-duration=" + config.tagDurationSeconds()
                    + "s, anchor-window=" + config.anchorAttributionWindowSeconds()
                    + "s, anchor-radius=" + config.anchorAttributionRadiusBlocks() + " blocks"
                    + ", penalty=" + config.penaltyMode().name().toLowerCase(java.util.Locale.ROOT)
                    + ", triggers[explosions=" + config.triggerExplosions()
                    + ", ender-pearl=" + config.triggerEnderPearl()
                    + ", combat-zone=" + config.triggerCombatZone()
                    + "], safe-zone-blocking=" + config.safeZoneMoveBlocking()
                    : "disabled in combat/config.yml (set enabled: true and /pvpcore reload to re-enable)")
                    + ").");
        } catch (Throwable failure) {
            // Leave no half-wired state behind; the caller logs the failure and continues without us.
            disable();
            throw failure;
        }
    }

    /** Called from PvPCorePlugin#onDisable. Cancels the shared task and drops all transient state. */
    @Override
    public void disable() {
        BukkitTask task = this.cleanupTask;
        if (task != null) {
            task.cancel();
            this.cleanupTask = null;
        }
        CombatListener currentListener = this.listener;
        if (currentListener != null) {
            HandlerList.unregisterAll(currentListener);
            this.listener = null;
        }
        CombatPenaltyService currentPenalty = this.penaltyService;
        if (currentPenalty != null) {
            HandlerList.unregisterAll(currentPenalty);
            this.penaltyService = null;
        }
        SafeZoneListener currentSafeZone = this.safeZoneListener;
        if (currentSafeZone != null) {
            HandlerList.unregisterAll(currentSafeZone);
            this.safeZoneListener = null;
        }
        this.resolver = null;
        AnchorAttributionTracker tracker = this.anchorTracker;
        if (tracker != null) {
            tracker.clear();
        }
        CombatManager manager = this.combatManager;
        if (manager != null) {
            manager.clearAll(CombatEndReason.PLUGIN_DISABLE);
        }
        this.anchorTracker = null;
        this.combatManager = null;
        this.config = null;
        this.messages = null;
    }

    private void cleanupExpired() {
        CombatManager manager = this.combatManager;
        AnchorAttributionTracker tracker = this.anchorTracker;
        if (manager != null) {
            manager.cleanupExpired();
        }
        if (tracker != null) {
            tracker.cleanupExpired();
        }
        scanCombatZone();
    }

    /**
     * Optional combat-zone trigger: every second (with the shared cleanup pass), tag nearby
     * players as opponents. Modelled on NikeyV1/CombatLog (GPL-3.0+, private use). Off by
     * default; spectators and creative players are never tagged (and never tag anyone).
     */
    private void scanCombatZone() {
        CombatManager manager = this.combatManager;
        if (manager == null) {
            return;
        }
        CombatConfig current = manager.config();
        if (!current.triggerCombatZone() || !manager.isEnabled()) {
            return;
        }
        double radius = current.combatZoneRadiusBlocks();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isZoneExempt(player)) {
                continue;
            }
            for (Player other : player.getWorld().getNearbyPlayers(player.getLocation(), radius)) {
                if (other == player || isZoneExempt(other)) {
                    continue;
                }
                manager.tagBoth(player.getUniqueId(), other.getUniqueId(), CombatCause.PLAYER_PROXIMITY);
            }
        }
    }

    private static boolean isZoneExempt(Player player) {
        GameMode mode = player.getGameMode();
        return player.isDead() || mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE;
    }

    /**
     * Re-reads {@code combat/config.yml} (e.g. from {@code /pvp-core reload}) and swaps the new
     * values into the live feature. Records already tracked are kept; only the config values
     * change. No-op when the feature is disabled. Main thread only.
     *
     * <p>Nothing has to be re-registered: the listeners read the live config through the manager
     * and the penalty service, so there is no unregister/register window on reload.</p>
     */
    @Override
    public void reload() {
        CombatManager manager = this.combatManager;
        AnchorAttributionTracker tracker = this.anchorTracker;
        CombatPenaltyService penalty = this.penaltyService;
        if (manager == null || tracker == null || penalty == null) {
            return;
        }
        CombatConfig newConfig;
        try {
            newConfig = loadConfig();
        } catch (RuntimeException failure) {
            // Keep the previous values; the caller logs the cause.
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "combat: could not reload combat/config.yml; keeping the previous settings.", failure);
            return;
        }
        this.messages.reload(); // refresh messages from disk, visible to all already-registered listeners
        this.config = newConfig;
        manager.reload(newConfig);
        tracker.reload(newConfig);
        penalty.reload(newConfig, this.messages);

        plugin.getLogger().info("combat: reloaded (enabled=" + (newConfig.enabled() ? "true" : "false")
                + ", tag-duration=" + newConfig.tagDurationSeconds()
                + "s, anchor-window=" + newConfig.anchorAttributionWindowSeconds()
                + "s, anchor-radius=" + newConfig.anchorAttributionRadiusBlocks() + " blocks"
                + ", penalty=" + newConfig.penaltyMode().name().toLowerCase(java.util.Locale.ROOT)
                + ", triggers[explosions=" + newConfig.triggerExplosions()
                + ", ender-pearl=" + newConfig.triggerEnderPearl()
                + ", combat-zone=" + newConfig.triggerCombatZone()
                + "], safe-zone-blocking=" + newConfig.safeZoneMoveBlocking()
                + ").");
    }

    /**
     * Loads {@code combat/config.yml} through the project's config system. Throws
     * {@link RuntimeException} if the file cannot be read.
     *
     * <p>Note on the double read: {@code ConfigUpdater.updateAll()} already created/merged this
     * file on enable, and {@link org.pvpcore.config.CoreConfigLoader#load(org.bukkit.plugin.java.JavaPlugin, String)}
     * is idempotent — it merges any keys added by a newer bundled default and saves only when the
     * file changed. Calling it again here is therefore safe and keeps {@code /pvpcore reload}
     * self-sufficient (new default keys appear without an editing trip through config.yml).</p>
     */
    private CombatConfig loadConfig() {
        org.bukkit.configuration.file.FileConfiguration yaml =
                org.pvpcore.config.CoreConfigLoader.load(plugin, CombatConfig.RELATIVE_PATH);
        return CombatConfig.from(yaml, plugin.getLogger());
    }

    private CombatMessages loadMessages() {
        return new CombatMessages(new File(plugin.getDataFolder(), "messages.yml"), plugin.getLogger());
    }

    /**
     * The combat session API for other features.
     *
     * <p>Returns {@code null} after the feature is disabled AND after a failed enable (in which
     * case {@code PvPCorePlugin} logs {@code SEVERE} with the cause) — integrations must tolerate
     * {@code null} and treat it as "combat tracking unavailable", not as "not in combat".</p>
     *
     * <p>Prefer {@link #isInCombat(UUID)} when you only need the boolean; it is null-safe.</p>
     */
    @Nullable
    public CombatManager getCombatManager() {
        return combatManager;
    }

    /**
     * Null-safe convenience: {@code true} when the player has a live combat session.
     * Returns {@code false} when the feature is disabled or failed to enable — i.e. combat
     * tracking unavailable reports as "not in combat", never as an exception.
     *
     * <p>Throws {@link IllegalStateException} if called off the server thread while the feature
     * is enabled (the manager enforces the same contract as every other call).</p>
     */
    public boolean isInCombat(UUID playerId) {
        CombatManager manager = this.combatManager;
        return manager != null && manager.isInCombat(playerId);
    }

    /** The validated combat config, or null after the feature is disabled / failed to enable. */
    @Nullable
    public CombatConfig getCombatConfig() {
        return config;
    }
}
