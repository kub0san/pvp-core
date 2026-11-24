package org.pvpcore.mechanic.knockbacksync;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.mechanic.Mechanic;

/**
 * Detects and exposes the external KnockbackSync plugin.
 *
 * <p>PvPCore does not port KnockbackSync or manage its lifecycle. This mechanic
 * verifies that the standalone KnockbackSync plugin is installed and enabled,
 * reports its state via {@code /pvp-core list}, and registers a late-start
 * listener so it can become active automatically if the external plugin enables
 * after PvPCore (PvPCore loads at {@code STARTUP}, KnockbackSync at
 * {@code POSTWORLD}).</p>
 *
 * <p>Because PvPCore does not own the external plugin, {@link #disable} only
 * clears PvPCore's intent flag; it never calls {@code PluginManager.disablePlugin}.
 * {@link #statusNote} reports the real external state so a mismatch between
 * PvPCore's intent and the actual plugin state is always visible in
 * {@code /pvp-core list}.</p>
 */
public final class KnockbackSyncMechanic implements Mechanic, Listener {

    private static final String PLUGIN_NAME = "knockbacksync";

    private PvPCorePlugin plugin;
    private volatile boolean active;
    private volatile boolean retryListenerRegistered;

    @Override
    public String id() {
        return "knockbacksync";
    }

    @Override
    public String displayName() {
        return "Knockback Sync";
    }

    @Override
    public String description() {
        return "Ping-compensated knockback provided by the external KnockbackSync plugin.";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public java.util.Set<String> requiredPlugins() {
        return java.util.Set.of(PLUGIN_NAME);
    }

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        this.plugin = plugin;

        Plugin kbSync = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        // Missing-plugin case is enforced by declared requirements; defensive guard kept for safety.
        if (kbSync == null) {
            plugin.getLogger().warning(
                    "Knockback Sync: external KnockbackSync plugin is not installed. "
                            + "Install it from Modrinth, or leave the mechanic disabled.");
            return false;
        }

        if (!kbSync.isEnabled()) {
            plugin.getLogger().info(
                    "Knockback Sync: external plugin is loaded (version "
                            + kbSync.getPluginMeta().getVersion()
                            + ") but not yet enabled. Will activate automatically once it enables.");
            ensureRetryListener();
            return false;
        }

        active = true;
        plugin.getLogger().info(
                "Knockback Sync hooked into external plugin (version "
                        + kbSync.getPluginMeta().getVersion() + ").");
        return true;
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        active = false;
        try {
            HandlerList.unregisterAll(this);
        } catch (Throwable ignored) {
            // Listener may not have registered.
        }
        retryListenerRegistered = false;
        // Do not call PluginManager.disablePlugin: PvPCore does not own the external plugin.
    }

    @Override
    public String statusNote() {
        Plugin kbSync = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (kbSync == null) {
            return "external plugin not installed";
        }
        if (!kbSync.isEnabled()) {
            return "waiting for external plugin to enable";
        }
        if (!active) {
            return "external plugin running; PvPCore mechanic off";
        }
        return "provided by external KnockbackSync plugin";
    }

    // ------------------------------------------------------------------
    // Late-start listener
    // ------------------------------------------------------------------

    private void ensureRetryListener() {
        if (retryListenerRegistered || plugin == null) {
            return;
        }
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            retryListenerRegistered = true;
        } catch (Throwable t) {
            plugin.getLogger().warning(
                    "Knockback Sync: could not register late-start listener: " + t);
        }
    }

    /**
     * Fires every time any plugin enables. If KnockbackSync just enabled and PvPCore's
     * stored config still asks for the mechanic, activate and persist the success.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (!active && event.getPlugin().getName().equalsIgnoreCase(PLUGIN_NAME)) {
            retryActivate("external plugin enabled");
        }
    }

    /** Fires once at the end of server startup. Final chance to activate. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        if (active) {
            return;
        }
        Plugin kbSync = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (kbSync != null && kbSync.isEnabled()) {
            retryActivate("server finished loading");
        } else if (kbSync == null) {
            plugin.getLogger().warning(
                    "Knockback Sync: external plugin never appeared. "
                            + "Install it from Modrinth, or disable the mechanic.");
        } else {
            plugin.getLogger().warning(
                    "Knockback Sync: external plugin is loaded but still disabled. "
                            + "Enable it, then run /pvp-core enable knockbacksync.");
        }
    }

    /**
     * Activates the mechanic if the external plugin is ready and PvPCore's stored
     * config still asks for it. Does nothing otherwise, so a user who disabled the
     * mechanic in config is not silently re-enabed by a late-start retry.
     */
    private void retryActivate(String reason) {
        if (plugin == null || active) {
            return;
        }
        if (!plugin.coreConfig().isEnabled(id(), false)) {
            return; // Stored config no longer wants this mechanic on.
        }
        Plugin kbSync = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (kbSync == null || !kbSync.isEnabled()) {
            return;
        }
        active = true;
        plugin.coreConfig().setEnabled(id(), true);
        plugin.getLogger().info(
                "Knockback Sync hooked into external plugin ("
                        + kbSync.getPluginMeta().getVersion()
                        + ") after " + reason + ".");
    }
}