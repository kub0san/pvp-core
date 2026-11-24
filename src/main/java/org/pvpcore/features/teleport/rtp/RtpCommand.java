package org.pvpcore.features.teleport.rtp;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pvpcore.config.Messages;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.TeleportService;
import org.pvpcore.gui.rtp.RtpDestinationPage;
import org.pvpcore.gui.rtp.RtpNavigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RtpCommand {

    private static final String ADMIN_PERMISSION = "pvpcore.teleportation.rtp.admin";

    private final TeleportModule teleport;
    private final TeleportService service;
    private final RtpModule rtp;

    RtpCommand(TeleportModule teleport, TeleportService service, RtpModule rtp) {
        this.teleport = teleport;
        this.service = service;
        this.rtp = rtp;
    }

    public void run(Player player, String[] args) {
        Messages messages = teleport.messages();
        RtpSettings settings = rtp.settings();
        if (!settings.enabled()) {
            player.sendMessage(messages.get("rtp-disabled"));
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("setup")) {
            if (!player.hasPermission(ADMIN_PERMISSION)) {
                player.sendMessage(messages.get("no-permission"));
                return;
            }
            new RtpNavigation(teleport, rtp).openManagement(player);
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("debug")) {
            debug(player, args.length >= 2 ? args[1] : null);
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            info(player);
            return;
        }

        if (args.length == 0) {
            RtpDestinationPage.open(teleport, service, rtp, player, 0);
            return;
        }

        // Fallback to direct teleport if they type /rtp <region>
        RtpRegion region = settings.region(args[0]);
        if (region == null) {
            player.sendMessage(messages.get("rtp-unknown-region",
                    "region", args[0], "regions", String.join(", ", settings.regions().keySet())));
            return;
        }

        if (!region.permission().isEmpty() && !player.hasPermission(region.permission())) {
            player.sendMessage(messages.get("no-permission"));
            return;
        }
        if (Bukkit.getWorld(region.worldName()) == null) {
            player.sendMessage(messages.get("rtp-world-unavailable", "world", region.worldName()));
            return;
        }

        // Pre-check so we don't start a background search only to have /begin reject it.
        int remaining = service.getRemainingCooldown(player);
        if (remaining > 0) {
            player.sendMessage(messages.get("cooldown-active", "seconds", String.valueOf(remaining)));
            return;
        }
        if (service.hasPending(player)) {
            player.sendMessage(messages.get("teleport-pending"));
            return;
        }

        // Fallback to direct teleport if they type /rtp <region>
        RtpService finder = rtp.service();
        
        // FIXED: Removed .join() to prevent deadlocking the main thread
        finder.acquire(region, player).thenAccept(loc -> {
            if (loc == null) {
                player.sendMessage(messages.get("rtp-failed"));
                return;
            }
            TeleportService.Options options = new TeleportService.Options(
                    settings.warmupSeconds(), settings.cooldownSeconds(), "rtp-failed", "rtp-success");
            service.begin(player, () -> loc, options);
        });
    }

    private void debug(Player player, String regionId) {
        Messages messages = teleport.messages();
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(messages.get("no-permission"));
            return;
        }
        player.sendMessage(messages.get("rtp-debug-header"));
        for (String line : rtp.service().debugReport(regionId)) {
            player.sendMessage(line);
        }
    }

    private void info(Player player) {
        Messages messages = teleport.messages();
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(messages.get("no-permission"));
            return;
        }
        RtpSettings settings = rtp.settings();
        int target = settings.poolEnabled() ? settings.poolSize() : 0;
        player.sendMessage(messages.get("rtp-info-header"));
        for (RtpRegion region : settings.regions().values()) {
            boolean loaded = Bukkit.getWorld(region.worldName()) != null;
            player.sendMessage(messages.get("rtp-info-region",
                    "id", region.id(),
                    "world", region.worldName(),
                    "loaded", loaded ? "yes" : "no",
                    "ready", String.valueOf(rtp.service().ready(region.id())),
                    "target", String.valueOf(target)));
        }
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (RtpRegion region : rtp.settings().regions().values()) {
            boolean allowed = region.permission().isEmpty() || sender.hasPermission(region.permission());
            if (allowed && region.id().startsWith(prefix)) {
                out.add(region.id());
            }
        }
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            if ("info".startsWith(prefix)) out.add("info");
            if ("setup".startsWith(prefix)) out.add("setup");
            if ("debug".startsWith(prefix)) out.add("debug");
        }
        return out;
    }
}