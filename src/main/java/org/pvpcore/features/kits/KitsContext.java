package org.pvpcore.features.kits;

import org.pvpcore.PvPCorePlugin;
import org.pvpcore.commands.kits.impl.features.RegearCommand;
import org.pvpcore.config.ConfigFiles;
import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.pvpcore.storage.StorageWorkQueue;
import org.pvpcore.storage.kits.StorageManager;
import org.pvpcore.util.kits.BroadcastManager;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.LocationAccess;
import org.pvpcore.util.kits.StyleManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Kits-scoped dependency container and lifecycle seam.
 * Introduced incrementally for Phase 2 architecture deepening; some callers
 * still use legacy static accessors during migration.
 */
public final class KitsContext {

    private final PvPCorePlugin plugin;

    private volatile FileConfiguration config;
    private Path configFile;

    private StorageManager storageManager;
    private StorageWorkQueue storageWork;
    private KitManager kitManager;

    private Lang lang;
    private StyleManager styleManager;
    private ItemFilter itemFilter;
    private LocationAccess locationAccess;
    private BroadcastManager broadcastManager;
    private KitShareManager kitShareManager;
    private KitRoomDataManager kitRoomDataManager;
    private RegearCommand regearCommand;

    public KitsContext(PvPCorePlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public PvPCorePlugin plugin() {
        return plugin;
    }

    public FileConfiguration config() {
        return config;
    }

    public void setConfig(FileConfiguration config) {
        this.config = config;
    }

    public Path configFile() {
        return configFile;
    }

    public void setConfigFile(Path configFile) {
        this.configFile = configFile;
    }

    public StorageManager storageManager() {
        return storageManager;
    }

    public void setStorageManager(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public StorageWorkQueue storageWork() {
        return storageWork;
    }

    public void setStorageWork(StorageWorkQueue storageWork) {
        this.storageWork = storageWork;
    }

    public KitManager kitManager() {
        return kitManager;
    }

    public void setKitManager(KitManager kitManager) {
        this.kitManager = kitManager;
    }

    public Lang lang() {
        return lang;
    }

    public void setLang(Lang lang) {
        this.lang = lang;
    }

    public StyleManager styleManager() {
        return styleManager;
    }

    public void setStyleManager(StyleManager styleManager) {
        this.styleManager = styleManager;
    }

    public ItemFilter itemFilter() {
        return itemFilter;
    }

    public void setItemFilter(ItemFilter itemFilter) {
        this.itemFilter = itemFilter;
    }

    public LocationAccess locationAccess() {
        return locationAccess;
    }

    public void setLocationAccess(LocationAccess locationAccess) {
        this.locationAccess = locationAccess;
    }

    public BroadcastManager broadcastManager() {
        return broadcastManager;
    }

    public void setBroadcastManager(BroadcastManager broadcastManager) {
        this.broadcastManager = broadcastManager;
    }

    public KitShareManager kitShareManager() {
        return kitShareManager;
    }

    public void setKitShareManager(KitShareManager kitShareManager) {
        this.kitShareManager = kitShareManager;
    }

    public KitRoomDataManager kitRoomDataManager() {
        return kitRoomDataManager;
    }

    public void setKitRoomDataManager(KitRoomDataManager kitRoomDataManager) {
        this.kitRoomDataManager = kitRoomDataManager;
    }

    public RegearCommand regearCommand() {
        return regearCommand;
    }

    public void setRegearCommand(RegearCommand regearCommand) {
        this.regearCommand = regearCommand;
    }

    public void saveConfig() {
        FileConfiguration cfg = config;
        Path file = configFile;
        if (cfg == null || file == null) return;
        try {
            ConfigFiles.write(cfg, file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save Kits config: " + e.getMessage());
        }
    }

    /**
     * Live kits master switch. Defaults to true when unset/missing.
     */
    public boolean isEnabled() {
        FileConfiguration cfg = config;
        return cfg == null || cfg.getBoolean("enabled", true);
    }

    /**
     * Configured kit/enderchest slot limit, clamped to [1, 99]. Defaults to 9.
     */
    public int maxKits() {
        FileConfiguration cfg = config;
        int configured = cfg == null ? 9 : cfg.getInt("kits.max-slots", 9);
        if (configured < 1) return 1;
        if (configured > 99) return 99;
        return configured;
    }
}
