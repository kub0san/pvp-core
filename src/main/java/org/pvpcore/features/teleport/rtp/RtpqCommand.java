package org.pvpcore.features.teleport.rtp;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pvpcore.config.Messages;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.gui.rtp.RtpQueuePage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Handles /rtpq: the 1v1 RTP matchmaking queue. */
public final class RtpqCommand {

    public static final String PERMISSION = "pvpcore.teleportation.rtpq";

    private final TeleportModule teleport;
    private final RtpModule rtp;

    public RtpqCommand(TeleportModule teleport, RtpModule rtp) {
        this.teleport = teleport;
        this.rtp = rtp;
    }

    public void run(Player player, String[] args) {
        Messages messages = teleport.messages();
        if (!rtp.settings().enabled()) {
            messages.send(player, "rtp-disabled");
            return;
        }
        if (!player.hasPermission(PERMISSION)) {
            messages.send(player, "no-permission");
            return;
        }
        if (args.length == 0) {
            RtpQueuePage.open(teleport, rtp, player);
            return;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            leave(player);
            return;
        }
        join(player, args[0]);
    }

    public void join(Player player, String regionId) {
        Messages messages = teleport.messages();
        if (!player.hasPermission(PERMISSION)) {
            messages.send(player, "no-permission");
            return;
        }
        RtpRegion region = rtp.settings().region(regionId);
        if (region == null) {
            messages.send(player, "rtp-unknown-region",
                    "region", regionId == null ? "" : regionId,
                    "regions", String.join(", ", rtp.settings().regions().keySet()));
            return;
        }
        if (!region.permission().isEmpty() && !player.hasPermission(region.permission())) {
            messages.send(player, "no-permission");
            return;
        }
        if (Bukkit.getWorld(region.worldName()) == null) {
            messages.send(player, "rtp-world-unavailable", "world", region.worldName());
            return;
        }

        RtpQueue.JoinResult result = rtp.rtpq().join(region.id(), player.getUniqueId());
        switch (result) {
            case ALREADY_IN_OTHER_REGION -> messages.send(player, "rtpq-already-queued",
                    "region", rtp.rtpq().queuedRegion(player.getUniqueId()).orElse(""));
            case ALREADY_IN_REGION -> { /* no-op: already waiting in this region */ }
            case JOINED -> messages.send(player, "rtpq-joined",
                    "region", region.id(), "queued", String.valueOf(rtp.rtpq().size(region.id())));
        }
    }

    public void leave(Player player) {
        Messages messages = teleport.messages();
        if (rtp.rtpq().leave(player.getUniqueId())) {
            messages.send(player, "rtpq-left");
        } else {
            messages.send(player, "rtpq-not-queued");
        }
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (RtpRegion region : rtp.settings().regions().values()) {
            if (region.id().equals("leave")) {
                continue; // reserved keyword
            }
            boolean allowed = region.permission().isEmpty() || sender.hasPermission(region.permission());
            if (allowed && Bukkit.getWorld(region.worldName()) != null && region.id().startsWith(prefix)) {
                out.add(region.id());
            }
        }
        if ("leave".startsWith(prefix)) {
            out.add("leave");
        }
        return out;
    }
}
