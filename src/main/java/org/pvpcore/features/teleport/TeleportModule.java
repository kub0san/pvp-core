package org.pvpcore.features.teleport;
import org.pvpcore.storage.teleport.TeleportStorage;

import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.Messages;
import org.pvpcore.module.PvPCoreModule;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.features.teleport.rtp.RtpModule;

import java.io.File;
import java.util.Set;

/** Lifecycle and wiring for teleportation. Server-wide, single server, no warps. */
public final class TeleportModule implements PvPCoreModule {

    static final String FOLDER = "teleport";
    private static final String LEGACY_FOLDER = "teleportation";

    private final JavaPlugin plugin;
    private final RequestManager requests = new RequestManager();

    private File folder;
    private TeleportSettings settings;
    private Messages messages;
    private TeleportStorage storage;
    private TeleportService service;
    private TeleportListener listener;
    private RtpModule rtp;
    private org.pvpcore.commands.teleport.TeleportCommands commands;

    public TeleportModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "teleport";
    }

    @Override
    public Set<String> dependsOn() {
        return Set.of();
    }

    @Override
    public void enable(PvPCorePlugin plugin) {
        enable();
    }

    /** Called from PvPCorePlugin#onEnable. */
    public void enable() {
        folder = migrateDataFolder();
        folder.mkdirs();
        saveDefaultIfMissing("config.yml");
        saveDefaultIfMissing("rtp.yml");

        storage = new TeleportStorage(new File(folder, "storage.yml"), plugin.getLogger());
        storage.load();
        loadFiles();

        service = new TeleportService(plugin, this);
        rtp = new RtpModule(plugin, this, service, folder);
        rtp.enable();

        commands = org.pvpcore.commands.teleport.TpCommands.registerBukkitCommands(plugin, this, service);

        listener = new TeleportListener(this, service);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        // Commands are always attached; the "enabled" setting is checked on every use,
        // so /pvp-core reload can switch teleportation on or off.
        plugin.getLogger().info("teleportation: " + (settings.enabled() ? "enabled" : "disabled in config")
                + " (request expiry " + settings.requestExpirySeconds() + "s, warmup "
                + settings.warmupSeconds() + "s, cooldown " + settings.cooldownSeconds() + "s).");
    }

    /** Called from PvPCorePlugin#onDisable. Cancels pending requests and warmups. */
    @Override
    public void disable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (rtp != null) {
            rtp.disable();
        }
        if (service != null) {
            service.shutdown();
        }
        requests.clear();
    }

    /** Called from /pvp-core reload. Re-reads config.yml, rtp.yml and messages.yml. */
    @Override
    public void reload() {
        loadFiles();
        if (rtp != null) {
            rtp.reload();
        }
        plugin.getLogger().info("teleportation: settings and messages reloaded.");
    }

    public TeleportSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public RequestManager requests() {
        return requests;
    }

    public TeleportStorage storage() {
        return storage;
    }

    /** Random teleport. Never null once {@link #enable()} has run. */
    public RtpModule rtp() {
        return rtp;
    }

    /** Bukkit executor/tab completer for teleport commands. Never null once {@link #enable()} has run. */
    public org.pvpcore.commands.teleport.TeleportCommands commands() {
        return commands;
    }

    public TeleportService service() {
        return service;
    }

    private void loadFiles() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        settings = TeleportSettings.from(org.pvpcore.config.teleport.TeleportConfig.loadConfig(plugin, folder));
        messages = Messages.load(plugin, messagesFile, "messages.yml", "teleportation");
    }

    /** Copies the bundled default only when the server owner has not created the file. */
    private void saveDefaultIfMissing(String name) {
        File target = new File(folder, name);
        if (!target.exists()) {
            plugin.saveResource(FOLDER + "/" + name, false);
        }
    }

    private File migrateDataFolder() {
        File current = new File(plugin.getDataFolder(), FOLDER);
        File legacy = new File(plugin.getDataFolder(), LEGACY_FOLDER);
        if (!current.exists() && legacy.exists() && !legacy.equals(current)) {
            if (legacy.renameTo(current)) {
                plugin.getLogger().info("Migrated data folder: " + LEGACY_FOLDER + " -> " + FOLDER);
            } else {
                plugin.getLogger().warning("Found legacy data folder '" + LEGACY_FOLDER
                        + "' but could not rename it to '" + FOLDER + "'. Using legacy folder for compatibility.");
                return legacy;
            }
        }
        return current;
    }
}
