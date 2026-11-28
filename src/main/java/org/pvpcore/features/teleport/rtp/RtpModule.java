package org.pvpcore.features.teleport.rtp;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.config.Messages;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.TeleportService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Lifecycle and wiring for random teleport. Owned by TeleportModule: it is enabled, reloaded
 * and disabled together with teleportation.
 */
public final class RtpModule {

    private static final String RESOURCE = "teleport/rtp.yml";

    private final JavaPlugin plugin;
    private final TeleportModule teleport;
    private final TeleportService teleportService;
    private final File file;

    private RtpSettings settings;
    private RtpService service;
    private RtpCommand command;
    private RtpQueue rtpq;
    private RtpqCommand rtpqCommand;
    private RtpMatchmaker matchmaker;
    private RtpQueueListener rtpqListener;

    public RtpModule(JavaPlugin plugin, TeleportModule teleport, TeleportService teleportService, File folder) {
        this.plugin = plugin;
        this.teleport = teleport;
        this.teleportService = teleportService;
        this.file = new File(folder, "rtp.yml");
    }

    /** Called from TeleportModule#enable, after the teleport service exists. */
    public void enable() {
        settings = load();
        service = new RtpService(plugin, teleport);
        command = new RtpCommand(teleport, teleportService, this);
        service.configure(settings, buildVerifiers(settings));

        rtpq = new RtpQueue();
        matchmaker = new RtpMatchmaker(plugin, teleportService, this::settings,
                () -> teleport.messages(), (region, player) -> service.acquire(region, player));
        rtpq.setMatchSink(matchmaker::startMatch);
        rtpqCommand = new RtpqCommand(teleport, this);
        rtpqListener = new RtpQueueListener(rtpq);
        plugin.getServer().getPluginManager().registerEvents(rtpqListener, plugin);

        log("enabled");
    }

    /** Called from TeleportModule#reload. Re-reads rtp.yml and clears the pools. */
    public void reload() {
        if (service == null) {
            return;
        }
        if (rtpq != null) {
            rtpq.clear();
        }
        settings = load();
        service.configure(settings, buildVerifiers(settings));
        log("reloaded");
    }

    /** Called from TeleportModule#disable. */
    public void disable() {
        if (rtpq != null) {
            rtpq.clear();
        }
        if (rtpqListener != null) {
            HandlerList.unregisterAll(rtpqListener);
        }
        if (service != null) {
            service.shutdown();
        }
    }

    public RtpSettings settings() {
        return settings;
    }

    public RtpService service() {
        return service;
    }

    public RtpCommand command() {
        return command;
    }

    public RtpQueue rtpq() {
        return rtpq;
    }

    public RtpqCommand rtpqCommand() {
        return rtpqCommand;
    }

    public RtpMatchmaker matchmaker() {
        return matchmaker;
    }

    public TeleportModule teleport() {
        return teleport;
    }

    private RtpSettings load() {
        return RtpSettings.from(org.pvpcore.config.teleport.TeleportConfig.loadRtp(plugin, file.getParentFile()), plugin.getLogger());
    }

    private List<LocationVerifier> buildVerifiers(RtpSettings loaded) {
        List<LocationVerifier> verifiers = new ArrayList<>();
        if (loaded.avoidWorldGuardRegions() && plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                verifiers.add(new WorldGuardRegionVerifier());
            } catch (LinkageError e) {
                plugin.getLogger().warning("rtp: WorldGuard is installed but its API could not be used; "
                        + "region avoidance is off: " + e);
            }
        }
        return verifiers;
    }

    private void log(String what) {
        plugin.getLogger().info("rtp: " + what + " (" + settings.regions().size() + " region(s), pool "
                + (settings.poolEnabled() ? settings.poolSize() + " per region" : "off") + ").");
    }

    public void updateSetting(String path, int value) {
        org.bukkit.configuration.file.FileConfiguration yaml = org.pvpcore.config.Messages.readYaml(plugin, file, RESOURCE);
        yaml.set(path, value);
        try {
            yaml.save(file);
            reload(); // Refresh the in-memory settings
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Could not save rtp.yml: " + e.getMessage());
        }
    }

    public void createRegion(String regionId, String worldName) {
        org.bukkit.configuration.file.FileConfiguration yaml = Messages.readYaml(plugin, file, RESOURCE);
        String path = "regions." + regionId;
        yaml.set(path + ".world", worldName);
        yaml.set(path + ".shape", "circle");
        yaml.set(path + ".center", "border");
        yaml.set(path + ".min-radius", 100);
        yaml.set(path + ".max-radius", 8096);
        yaml.set(path + ".vertical", "surface");
        saveAndReload(yaml);
    }

    public void updateRegionSetting(String regionId, String key, int value) {
        org.bukkit.configuration.file.FileConfiguration yaml = Messages.readYaml(plugin, file, RESOURCE);
        yaml.set("regions." + regionId + "." + key, value);
        saveAndReload(yaml);
    }

    public void deleteRegion(String regionId) {
        org.bukkit.configuration.file.FileConfiguration yaml = Messages.readYaml(plugin, file, RESOURCE);
        yaml.set("regions." + regionId, null);
        saveAndReload(yaml);
    }

    private void saveAndReload(org.bukkit.configuration.file.FileConfiguration yaml) {
        try {
            yaml.save(file);
            reload();
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Could not save rtp.yml: " + e.getMessage());
        }
    }
}
