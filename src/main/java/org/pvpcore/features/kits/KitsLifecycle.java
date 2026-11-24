package org.pvpcore.features.kits;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.commands.kits.impl.features.RegearCommand;
import org.pvpcore.config.kits.ConfigMigrator;
import org.pvpcore.config.kits.KitsConfigLoader;
import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.pvpcore.listeners.antiexploit.CommandListener;
import org.pvpcore.listeners.antiexploit.ShulkerDropItemsListener;
import org.pvpcore.listeners.kits.AutoRekitListener;
import org.pvpcore.listeners.kits.JoinListener;
import org.pvpcore.listeners.kits.KitMenuCloseListener;
import org.pvpcore.listeners.kits.OldDeathDropListener;
import org.pvpcore.listeners.kits.QuitListener;
import org.pvpcore.storage.kits.StorageConnectionException;
import org.pvpcore.storage.kits.StorageManager;
import org.pvpcore.util.kits.BackupManager;
import org.pvpcore.util.kits.BroadcastManager;
import org.pvpcore.util.kits.KitSlots;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.StyleManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Owns the full lifecycle (enable / reload / disable) of the Kits module.
 *
 * <p>Modernized for Paper/Folia: every scheduling call now goes through
 * {@link io.papermc.paper.threadedregions.scheduler.AsyncScheduler} or
 * {@link io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler} so the
 * module keeps working when the server ticks multiple regions independently.
 *
 * <p>{@link #enable} is idempotent — a second invocation is logged and ignored. All
 * stateful bootstrapping happens inside a single try/finally so a half‑initialized
 * module is rolled back instead of lingering.
 */
final class KitsLifecycle {

    /** Guards against accidental double‑enable from multiple bootstrap paths. */
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    /** Keep‑alive task handle; cancelled on disable so the DB can close cleanly. */
    private volatile ScheduledTask keepAliveTask;

    /** Listener instances registered by this module, retained so rollback can unregister them. */
    private final List<Listener> registeredListeners = new ArrayList<>();

    // ---------------------------------------------------------------------
    // Enable
    // ---------------------------------------------------------------------

    void enable(Kits kits, PvPCorePlugin pvpCore) {
        if (!enabled.compareAndSet(false, true)) {
            pvpCore.getLogger().warning("Kits.enable() was called while already enabled; ignoring.");
            return;
        }

        Kits.plugin = pvpCore;
        Kits.context = new KitsContext(pvpCore);
        notice(kits);
        Kits.storageManager = null;
        Kits.context.setStorageManager(null);

        try {
            LegacyKitsImport.importIfNeeded(Kits.plugin);

            if (!new ConfigMigrator(Kits.plugin).migrate()) {
                fail("Kits failed to load: config migration failed; see errors above.");
                return;
            }

            KitsConfigLoader.LoadedConfig loaded = new KitsConfigLoader(Kits.plugin).load();
            if (loaded == null) {
                fail("Kits failed to load: could not read Kits/config.yml.");
                return;
            }

            // The module always initializes — the 'enabled' flag gates every use
            // (commands, listeners, broadcasts) so /pvpcore reload can flip it both ways.
            Kits.config = loaded.config();
            Kits.configFile = loaded.file();
            Kits.context.setConfig(loaded.config());
            Kits.context.setConfigFile(loaded.file());

            if (!isContextEnabled(Kits.context)) {
                Kits.plugin.getLogger().info(
                        "Kits module is disabled by Kits/config.yml; features stay off until enabled and "
                                + "/pvpcore reload (or restart).");
            }

            initSingletons(Kits.context);

            // Storage setup. Kept behind attemptDatabaseConnection so an init failure
            // does not leave the module half-booted.
            Kits.plugin.getLogger().info("Using storage type: sqlite");
            Kits.storageManager = new org.pvpcore.storage.kits.sql.SQLite(Kits.plugin);
            Kits.context.setStorageManager(Kits.storageManager);

            if (!attemptDatabaseConnection(kits, true)) {
                return;
            }

            try {
                Kits.storageManager.init();
            } catch (StorageConnectionException e) {
                fail("Failed to initialize the database. Disabling Kits.", e);
                closeDatabaseConnection(kits);
                return;
            }

            initManagers(Kits.context);
            KitManager recovered = Kits.context.kitManager();
            if (recovered != null) {
                recovered.replayFailedWrites();
            }
            loadPublicKitsIdsFromConfig(Kits.context);
            Kits.plugin.getLogger().info("Public Kit Configuration Loaded");

            initBackups(kits);
            startKeepAliveTask();

            try {
                loadDatabaseData(kits, Kits.context);
            } catch (RuntimeException error) {
                fail("Could not load saved kit data. Disabling Kits.", error);
                return;
            }
            Kits.plugin.getLogger().info("Database data loaded");

            kits.updateChecker = new UpdateChecker(Kits.plugin);

            registerCommands(Kits.context);
            registerListeners(kits, Kits.context);

            if (isContextEnabled(Kits.context)) {
                BroadcastManager broadcasts = new BroadcastManager(Kits.context);
                broadcasts.startScheduledBroadcast();
            }

            kits.updateChecker.printStartupStatus();
        } catch (RuntimeException unexpected) {
            // Catch-all so a half-initialized module never lingers. Rollback is best-effort;
            // a secondary failure during teardown is logged but not swallowed silently.
            fail("Unexpected error during Kits enable; rolling back.", unexpected);
            try {
                disable(kits);
            } catch (RuntimeException rollbackError) {
                if (Kits.plugin != null) {
                    Kits.plugin.getLogger().log(Level.SEVERE,
                            "Rollback after a failed Kits enable threw again", rollbackError);
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Reload
    // ---------------------------------------------------------------------

    void reload() {
        if (Kits.plugin == null || !Kits.plugin.isEnabled()) {
            return;
        }

        // Read file off the main thread so an oversized / locked config cannot stall ticks.
        Bukkit.getAsyncScheduler().runNow(Kits.plugin, asyncTask -> {
            KitsConfigLoader.LoadedConfig loaded;
            try {
                loaded = new KitsConfigLoader(Kits.plugin).load();
            } catch (RuntimeException e) {
                Kits.plugin.getLogger().log(Level.SEVERE,
                        "Kits reload aborted: could not read Kits/config.yml", e);
                return;
            }
            if (loaded == null) {
                Kits.plugin.getLogger().severe("Kits reload aborted: could not read Kits/config.yml");
                return;
            }

            // Apply on the global region (main thread equivalent, Folia-safe).
            Bukkit.getGlobalRegionScheduler().execute(Kits.plugin, () -> {
                if (Kits.plugin == null) return;

                KitsContext ctx = Kits.context;
                if (ctx == null) {
                    ctx = new KitsContext(Kits.plugin);
                    Kits.context = ctx;
                }
                Kits.config = loaded.config();
                Kits.configFile = loaded.file();
                ctx.setConfig(loaded.config());
                ctx.setConfigFile(loaded.file());

                // Singletons/services that snapshot configuration in their constructors.
                KitSlots.init(Kits.plugin, ctx.config());
                Lang.shutdown();
                StyleManager.shutdown();
                ItemFilter.shutdown();
                org.pvpcore.util.kits.LocationAccess.shutdown();
                BroadcastManager.shutdown();

                initSingletons(ctx);

                if (ctx.kitRoomDataManager() != null) {
                    ctx.kitRoomDataManager().refreshWhitelist();
                }
                // Premade categories live in their own file; re-read so a reload
                // picks up an edited premade-categories.yml.
                org.pvpcore.gui.kits.data.PremadeStore.init(Kits.plugin, ctx);

                KitManager km = ctx.kitManager();
                if (km != null) {
                    km.getPublicKitList().clear();
                    loadPublicKitsIdsFromConfig(ctx);
                    km.getPublicKitList().forEach(kit -> km.loadPublicKitFromDB(kit.id));
                }

                org.pvpcore.commands.kits.KitsCommands.reloadBukkitCommands(Kits.plugin, ctx);

                if (isContextEnabled(ctx)) {
                    BroadcastManager broadcasts = new BroadcastManager(ctx);
                    broadcasts.startScheduledBroadcast();
                }
                Kits.plugin.getLogger().info("Kits configuration reloaded"
                        + (isContextEnabled(ctx)
                        ? "."
                        : " (kits are disabled in kits/config.yml; set enabled: true and "
                        + "/pvpcore reload to turn them on)."));
            });
        });
    }

    // ---------------------------------------------------------------------
    // Disable
    // ---------------------------------------------------------------------

    void disable(Kits kits) {
        if (Kits.plugin == null) {
            enabled.set(false);
            return;
        }

        // 1. Stop scheduled work first so nothing dereferences resources we are about to close.
        ScheduledTask task = keepAliveTask;
        if (task != null) {
            task.cancel();
            keepAliveTask = null;
        }

        // 2. Tear down commands and listeners. Bukkit unregisters on plugin disable, but a
        // failed enable rolls back without disabling the plugin, so these must be explicit.
        unregisterListeners();

        // 3. Tear down commands.
        if (Kits.plugin instanceof PvPCorePlugin p) {
            org.pvpcore.commands.kits.KitsCommands.disableBukkitCommands(p, Kits.context);
        } else {
            RegearCommand previous = Kits.regearCommand;
            if (previous != null) {
                org.bukkit.event.HandlerList.unregisterAll(previous);
            }
        }

        // 4. Tear down features in reverse dependency order.
        quietShutdown("UpdateChecker", () -> {
            if (kits.updateChecker != null) kits.updateChecker.close();
        });
        quietShutdown("BackupManager", () -> {
            if (kits.backupManager != null) kits.backupManager.shutdown();
        });

        KitManager km = Kits.context != null ? Kits.context.kitManager() : null;
        if (km != null) {
            quietShutdown("KitManager", km::shutdown);
        }

        // Cached singletons (scheduled tasks / audiences) before storage goes away.
        quietShutdown("KitShareManager",    KitShareManager::shutdown);
        quietShutdown("KitRoomDataManager", KitRoomDataManager::shutdown);
        quietShutdown("Lang",               Lang::shutdown);
        quietShutdown("StyleManager",       StyleManager::shutdown);
        quietShutdown("ItemFilter",         ItemFilter::shutdown);
        quietShutdown("LocationAccess",     org.pvpcore.util.kits.LocationAccess::shutdown);
        quietShutdown("BroadcastManager",   BroadcastManager::shutdown);
        quietShutdown("GUI",                org.pvpcore.gui.kits.GUI::shutdown);

        // 5. Close storage last.
        closeDatabaseConnection(kits);
        Kits.storageManager = null;

        // 6. Clear static state so the old classloader can be GC'd on /reload.
        kits.updateChecker = null;
        kits.backupManager = null;
        Kits.config = null;
        Kits.configFile = null;
        Kits.regearCommand = null;
        if (Kits.context != null) {
            Kits.context.setStorageManager(null);
            Kits.context.setKitManager(null);
            Kits.context.setLang(null);
            Kits.context.setStyleManager(null);
            Kits.context.setItemFilter(null);
            Kits.context.setLocationAccess(null);
            Kits.context.setBroadcastManager(null);
            Kits.context.setKitShareManager(null);
            Kits.context.setKitRoomDataManager(null);
            Kits.context.setConfig(null);
            Kits.context.setConfigFile(null);
        }
        Kits.context = null;
        Kits.plugin = null;

        enabled.set(false);
    }

    // ---------------------------------------------------------------------
    // Config loading
    // ---------------------------------------------------------------------

    static void loadPublicKitsIdsFromConfig(KitsContext ctx) {
        if (ctx == null || ctx.config() == null || ctx.kitManager() == null || Kits.plugin == null) {
            return;
        }
        ConfigurationSection publicKitsSection = ctx.config().getConfigurationSection("publickits");
        if (publicKitsSection == null) {
            Kits.plugin.getLogger().warning("No public kits found in config!");
            return;
        }

        KitManager km = ctx.kitManager();
        for (String key : publicKitsSection.getKeys(false)) {
            try {
                String name = ctx.config().getString("publickits." + key + ".name", key);
                String iconName = ctx.config().getString("publickits." + key + ".icon", "");
                Material icon = (iconName == null || iconName.isBlank())
                        ? null
                        : Material.matchMaterial(iconName);

                if (icon == null || !icon.isItem() || icon.isAir()) {
                    Kits.plugin.getLogger().warning(
                            "Public kit '" + key + "' has icon " + iconName
                                    + ", which does not exist on this server - skipping the kit");
                    continue;
                }

                km.getPublicKitList().add(new PublicKit(key, Objects.requireNonNull(name), icon));
            } catch (RuntimeException e) {
                // One malformed entry must not poison the whole batch.
                Kits.plugin.getLogger().log(Level.WARNING,
                        "Skipping malformed public kit '" + key + "'", e);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Data loading
    // ---------------------------------------------------------------------

    void loadDatabaseData(Kits kits, KitsContext ctx) {
        if (ctx == null) return;

        if (ctx.kitRoomDataManager() != null) {
            ctx.kitRoomDataManager().loadFromDB();
        }

        KitManager km = ctx.kitManager();
        if (km == null) return;

        km.getPublicKitList().forEach(kit -> km.loadPublicKitFromDB(kit.id));

        // Snapshot online UUIDs on the calling thread; the load itself is async by contract.
        List<UUID> onlineUuids = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();
        onlineUuids.forEach(km::loadPlayerDataAsync);
    }

    static boolean isContextEnabled(KitsContext ctx) {
        FileConfiguration cfg = ctx != null ? ctx.config() : Kits.config;
        return cfg == null || cfg.getBoolean("enabled", true);
    }

    // ---------------------------------------------------------------------
    // Database
    // ---------------------------------------------------------------------

    boolean attemptDatabaseConnection(Kits kits, boolean disableOnFail) {
        StorageManager sm = Kits.context != null ? Kits.context.storageManager() : Kits.storageManager;
        try {
            if (sm == null) {
                throw new StorageConnectionException("Storage manager is not initialized.");
            }
            sm.connect();
            if (!sm.isConnected()) {
                throw new StorageConnectionException(
                        "Expected to be connected to the database, but the driver reports it is not.");
            }
            return true;
        } catch (StorageConnectionException e) {
            if (Kits.plugin != null) {
                Kits.plugin.getLogger().warning("Database connection failed: " + e.getMessage());
                if (disableOnFail) {
                    Kits.plugin.getLogger().warning("Disabling Kits module.");
                    Kits.plugin.getLogger().severe("Kits failed to load; see errors above.");
                }
            }
            return false;
        }
    }

    void closeDatabaseConnection(Kits kits) {
        StorageManager sm = Kits.context != null ? Kits.context.storageManager() : Kits.storageManager;
        if (sm == null) return;
        try {
            sm.close();
        } catch (StorageConnectionException e) {
            if (Kits.plugin != null) {
                Kits.plugin.getLogger().log(Level.WARNING,
                        "Failed to close the database connection", e);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void initSingletons(KitsContext ctx) {
        Lang lang = new Lang(Kits.plugin);
        StyleManager styleManager = new StyleManager(ctx);
        ItemFilter itemFilter = new ItemFilter(ctx);
        org.pvpcore.util.kits.LocationAccess locationAccess =
                new org.pvpcore.util.kits.LocationAccess(ctx);
        BroadcastManager broadcastManager = new BroadcastManager(ctx);

        ctx.setLang(lang);
        ctx.setStyleManager(styleManager);
        ctx.setItemFilter(itemFilter);
        ctx.setLocationAccess(locationAccess);
        ctx.setBroadcastManager(broadcastManager);
    }

    private void initManagers(KitsContext ctx) {
        KitManager kitManager = new KitManager(ctx);
        ctx.setKitManager(kitManager);
        KitShareManager kitShareManager = new KitShareManager(ctx);
        ctx.setKitShareManager(kitShareManager);
        KitRoomDataManager kitRoomDataManager = new KitRoomDataManager(ctx);
        ctx.setKitRoomDataManager(kitRoomDataManager);
    }

    private void initBackups(Kits kits) {
        kits.backupManager = new BackupManager(Kits.plugin, Kits.storageManager);
        if (kits.backupManager.isEnabled()) {
            Kits.plugin.getLogger().info("Backup system initialized for file-based storage");
        } else {
            Kits.plugin.getLogger().info("Backup system disabled in configuration");
        }
    }

    private void registerCommands(KitsContext ctx) {
        // Wiring is centralized in the global commands package.
        org.pvpcore.commands.kits.KitsCommands.registerBukkitCommands(Kits.plugin, ctx);
    }

    private void registerListeners(Kits kits, KitsContext ctx) {
        registerListener(new JoinListener(Kits.plugin, ctx, kits.updateChecker), Kits.plugin);
        registerListener(new QuitListener(Kits.plugin, ctx), Kits.plugin);
        registerListener(new KitMenuCloseListener(ctx), Kits.plugin);
        registerListener(new AutoRekitListener(Kits.plugin, ctx), Kits.plugin);

        FileConfiguration cfg = ctx.config();
        if (cfg != null && cfg.getBoolean("death.condensed-drops", false)) {
            registerListener(new OldDeathDropListener(), Kits.plugin);
        }
        if (cfg != null && cfg.getBoolean("anti-exploit.block-spaces-in-commands", false)) {
            registerListener(new CommandListener(), Kits.plugin);
        }
        if (cfg != null && cfg.getBoolean("anti-exploit.prevent-shulkers-dropping-items", false)) {
            registerListener(new ShulkerDropItemsListener(), Kits.plugin);
        }
    }

    void registerListener(Listener listener, Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    /** Unregisters only listeners owned by this module; needed on a failed-enable rollback. */
    void unregisterListeners() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
    }

    /**
     * Starts the periodic keep-alive / reconnect task. Handles Folia's async scheduler
     * so the module works when the server ticks independent regions.
     *
     * <p>Note: we deliberately do <b>not</b> reconnect synchronously inside the task.
     * If the DB is down, we let the scheduler keep firing so the next tick attempts
     * recovery, and log the failure once per cycle.
     */
    private void startKeepAliveTask() {
        this.keepAliveTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                Kits.plugin,
                task -> {
                    try {
                        StorageManager sm = Kits.context != null
                                ? Kits.context.storageManager()
                                : Kits.storageManager;
                        KitManager km = Kits.context != null ? Kits.context.kitManager() : null;

                        if (sm != null && sm.isConnected()) {
                            sm.keepAlive();
                            if (km != null) km.retryFailedWrites();
                        } else {
                            if (Kits.plugin != null) {
                                Kits.plugin.getLogger().warning(
                                        "Database connection lost; attempting reconnect.");
                            }
                            attemptDatabaseConnection(null, false);
                        }
                    } catch (StorageConnectionException e) {
                        if (Kits.plugin != null) {
                            Kits.plugin.getLogger().log(Level.WARNING,
                                    "Database keep-alive failed", e);
                        }
                    }
                },
                30L, 30L, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void fail(String message) {
        if (Kits.plugin != null) Kits.plugin.getLogger().severe(message);
    }

    private void fail(String message, Throwable t) {
        if (Kits.plugin != null) Kits.plugin.getLogger().log(Level.SEVERE, message, t);
    }

    private void quietShutdown(String label, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            if (Kits.plugin != null) {
                Kits.plugin.getLogger().log(Level.WARNING, label + " shutdown threw", e);
            }
        }
    }

    // ---------------------------------------------------------------------
    // License notice
    // ---------------------------------------------------------------------

    void notice(Kits kits) {
        String notice = """
                * Kits is free software: you can redistribute it and/or modify it under
                * the terms of the GNU Affero General Public License as published by the
                * Free Software Foundation, either version 3 of the License, or (at your
                * option) any later version.
                *
                * Kits is distributed in the hope that it will be useful, but WITHOUT ANY
                * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
                * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
                * more details.
                *
                * You should have received a copy of the GNU Affero General Public License
                * along with Kits. If not, see <https://www.gnu.org/licenses/>.""";

        String otherInfo = """
                * All users must be provided with the source code of the software, as per the AGPL-3.0 license.
                * If you are using a modified version of Kits, you must make the source code of your
                * modified version available to all users, as per the AGPL-3.0 license.
                * Consider modifying the /aboutperplayerkit command to include a link to your modified source code.
                """;

        Kits.plugin.getLogger().info(notice);
        Kits.plugin.getLogger().info(otherInfo);
    }
}