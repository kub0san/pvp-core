/*
 * Copyright 2022-2026 Noah Ross
 *
 * This file is part of Kits.
 *
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
 * along with Kits. If not, see <https://www.gnu.org/licenses/>.
 */

package org.pvpcore.features.kits;
import org.pvpcore.config.ConfigFiles;

import org.pvpcore.PvPCorePlugin;
import org.pvpcore.commands.kits.impl.features.RegearCommand;
import org.pvpcore.module.PvPCoreModule;
import org.pvpcore.storage.kits.StorageManager;
import org.pvpcore.util.kits.BackupManager;
import org.pvpcore.util.kits.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Set;

public final class Kits implements PvPCoreModule {

    /** why: legacy public field shim retained for external/legacy compatibility (handled last); internal code uses module/context plugin references. */
    @Deprecated
    public static org.pvpcore.PvPCorePlugin plugin;
    /** why: legacy public field shim retained for external/legacy compatibility (handled last); internal code uses context storage manager. */
    @Deprecated
    public static StorageManager storageManager;
    // volatile: reload() reads the file on an async thread and the main thread reads it on every use.
    static volatile org.bukkit.configuration.file.FileConfiguration config; // why: static config cache retained for legacy static config access and async reload compatibility.
    static java.nio.file.Path configFile; // why: static config path retained for legacy static config-file access.
    static volatile KitsContext context; // why: static context pointer retained for legacy static context access and module lifecycle compatibility.
    BackupManager backupManager;
    UpdateChecker updateChecker;
    static RegearCommand regearCommand; // why: legacy static regear command holder retained for external/legacy compatibility and context sync.

    private static final KitsLifecycle LIFECYCLE = new KitsLifecycle();

    /** why: legacy static plugin accessor retained for external/legacy compatibility; prefer {@link KitsContext#plugin()}. */
    @Deprecated
    public static Plugin getPlugin() {
        return plugin;
    }

    /** why: legacy static context accessor retained for external/legacy compatibility; prefer {@link #kitsContext()}. */
    public static KitsContext context() {
        return context;
    }

    /** Instance accessor for the active kits context (preferred over static shim). */
    public KitsContext kitsContext() {
        return context;
    }

    /** @deprecated why: legacy static config accessor; delegate to {@link KitsContext#config()} as the single source. */
    @Deprecated
    public static org.bukkit.configuration.file.FileConfiguration config() {
        KitsContext ctx = context;
        if (ctx != null && ctx.config() != null) {
            return ctx.config();
        }
        return config;
    }
    /** why: legacy static config setter retained for external/legacy compatibility and config loading paths. */
    public static void setConfig(org.bukkit.configuration.file.FileConfiguration c) {
        config = c;
        KitsContext ctx = context;
        if (ctx != null) ctx.setConfig(c);
    }

    /**
     * The kits master switch, checked on every use (live config, applied on /pvpcore reload).
     * Defaults to true when unset or when the config has not been loaded yet â€” a missing key
     * must never silently disable the module.
     */
    /**
     * @deprecated Use {@link Kits#context()} then {@link KitsContext#config()} and check {@code enabled}.
     * <p>Why retained: legacy static enabled-state shim; call sites migrate in T11-T13.
     */
    @Deprecated
    public static boolean isEnabled() {
        org.bukkit.configuration.file.FileConfiguration cfg = config();
        return cfg == null || cfg.getBoolean("enabled", true);
    }

    /** why: legacy static module-disabled gate retained for external/legacy compatibility; internal callers prefer context-aware checks. */
    public static boolean disabled(CommandSender sender) {
        KitsContext ctx = context;
        org.bukkit.configuration.file.FileConfiguration cfg = ctx != null ? ctx.config() : config;
        if (cfg == null || cfg.getBoolean("enabled", true)) {
            return false;
        }
        Lang lang = ctx != null ? ctx.lang() : null;
        if (lang != null) {
            lang.sendNoPrefix(sender, "error.module-disabled");
        } else {
            sender.sendMessage("Kits are currently disabled.");
        }
        return true;
    }
    /** why: legacy static config-file setter retained for external/legacy compatibility and config loading paths. */
    public static void setConfigFile(java.nio.file.Path p) {
        configFile = p;
        if (context != null) context.setConfigFile(p);
    }

    /** why: legacy static config-file accessor retained for external/legacy compatibility and config loading paths. */
    public static java.nio.file.Path getConfigFile() {
        return configFile != null ? configFile : (context != null ? context.configFile() : null);
    }

    /** why: legacy static config save shim retained for external/legacy compatibility; prefer context save methods. */
    public static void saveConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = config();
        java.nio.file.Path file = getConfigFile();
        if (cfg == null || file == null) return;
        try {
            ConfigFiles.write(cfg, file);
        } catch (java.io.IOException e) {
            if (plugin != null) plugin.getLogger().severe("Failed to save Kits config: " + e.getMessage());
        }
    }

    /** why: legacy static config reload shim retained for external/legacy compatibility; prefer context/config reload flows. */
    public static void reloadConfig() {
        java.nio.file.Path file = getConfigFile();
        if (file == null) return;
        try {
            org.bukkit.configuration.file.FileConfiguration loaded = ConfigFiles.read(file);
            config = loaded;
            if (context != null) context.setConfig(loaded);
        } catch (java.io.IOException e) {
            if (plugin != null) plugin.getLogger().severe("Failed to reload Kits config: " + e.getMessage());
        }
    }

    /** why: legacy static regear command accessor retained for external/legacy compatibility; prefer {@link KitsContext#regearCommand()}. */
    public static RegearCommand getRegearCommand() {
        KitsContext ctx = context;
        return ctx != null ? ctx.regearCommand() : regearCommand;
    }

    /** why: legacy static regear command setter retained for external/legacy compatibility; prefer {@link KitsContext#setRegearCommand(RegearCommand)}. */
    public static void setRegearCommand(RegearCommand command) {
        regearCommand = command;
        KitsContext ctx = context;
        if (ctx != null) ctx.setRegearCommand(command);
    }

    @Override
    public String name() {
        return "kits";
    }

    @Override
    public Set<String> dependsOn() {
        return Set.of();
    }

    @Override
    public void reload() {
        reloadModule();
    }

    /**
     * Re-applies Kits/config.yml without tearing down storage, caches, or the player
     * data. Safe to call from /pvp-core reload. Does not re-run the v1/v2 -> v3
     * migration; that stays a one-time operation at startup.
     */
    public void reloadModule() {
        LIFECYCLE.reload();
    }

    /** why: legacy static reload shim removed in favor of {@link #reloadModule()}/{@link #reload()}; no in-repo callers remained. */

    public org.bukkit.configuration.file.FileConfiguration getConfig() { return config(); }
    public java.util.logging.Logger getLogger() { return plugin.getLogger(); }
    public org.bukkit.Server getServer() { return plugin.getServer(); }
    public java.io.File getDataFolder() { return plugin.getDataFolder(); }
    public java.io.InputStream getResource(String name) { return plugin.getResource(name); }
    public void saveDefaultConfig() { }


    @Override
    public void enable(org.pvpcore.PvPCorePlugin pvpCore) {
        LIFECYCLE.enable(this, pvpCore);
    }

    @Override
    public void disable() {
        LIFECYCLE.disable(this);
    }
}
