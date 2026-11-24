package org.pvpcore.commands.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.pvpcore.config.Messages;
import org.pvpcore.features.teleport.RequestManager;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.TeleportService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Handles /tpa, /tpahere, /tpaccept, /tpdeny, /tpcancel, /tphere, /back, /spawn, /setspawn, /rtp. */
public final class TeleportCommands implements CommandExecutor, TabCompleter {

    public static final List<String> NAMES = List.of(
            "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tphere", "back", "spawn", "setspawn", "rtp");

    private static final String BYPASS_COOLDOWN = "pvpcore.teleportation.bypass.cooldown";

    private final TeleportModule module;
    private final TeleportService service;

    public TeleportCommands(TeleportModule module, TeleportService service) {
        this.module = module;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = module.messages();
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("player-only"));
            return true;
        }
        if (!player.hasPermission("pvpcore.teleportation." + name)) {
            player.sendMessage(messages.get("no-permission"));
            return true;
        }
        if (!module.settings().enabled()) {
            player.sendMessage(messages.get("teleport-disabled"));
            return true;
        }

        switch (name) {
            case "tpa" -> request(player, args, RequestManager.Type.TO_TARGET, "/tpa <player>");
            case "tpahere" -> request(player, args, RequestManager.Type.TARGET_HERE, "/tpahere <player>");
            case "tpaccept" -> accept(player);
            case "tpdeny" -> deny(player);
            case "tpcancel" -> cancel(player);
            case "tphere" -> tphere(player, args);
            case "back" -> back(player);
            case "spawn" -> spawn(player);
            case "setspawn" -> setSpawn(player);
            case "rtp" -> module.rtp().command().run(player, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void request(Player player, String[] args, RequestManager.Type type, String usage) {
        Messages messages = module.messages();
        if (args.length < 1) {
            player.sendMessage(messages.get("usage", "usage", usage));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(messages.get("player-not-found", "player", args[0]));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(messages.get("self-target"));
            return;
        }

        long now = System.currentTimeMillis();

        if (!player.hasPermission(BYPASS_COOLDOWN)) {
            long cooldownMs = module.settings().requestCooldownSeconds() * 1000L;
            long remaining = module.requests().cooldownRemaining(player.getUniqueId(), now, cooldownMs);
            if (remaining > 0) {
                long seconds = (remaining + 999) / 1000;
                player.sendMessage(messages.get("request-cooldown", "seconds", String.valueOf(seconds)));
                return;
            }
        }

        Optional<RequestManager.Request> existing =
                module.requests().pendingFrom(player.getUniqueId(), now);
        if (existing.isPresent()
                && existing.get().target().equals(target.getUniqueId())
                && existing.get().type() == type) {
            player.sendMessage(messages.get("request-already-pending", "player", target.getName()));
            return;
        }

        int expiry = module.settings().requestExpirySeconds();
        module.requests().submit(player.getUniqueId(), target.getUniqueId(), type,
                now, expiry * 1000L);
        player.sendMessage(messages.get("request-sent",
                "player", target.getName(), "seconds", String.valueOf(expiry)));
        String key = type == RequestManager.Type.TO_TARGET ? "request-received" : "request-received-here";
        target.sendMessage(messages.get(key, "player", player.getName()));
    }

    private void accept(Player player) {
        Messages messages = module.messages();
        Optional<RequestManager.Request> taken =
                module.requests().takeFor(player.getUniqueId(), System.currentTimeMillis());
        if (taken.isEmpty()) {
            player.sendMessage(messages.get("request-none"));
            return;
        }
        RequestManager.Request request = taken.get();
        Player requester = Bukkit.getPlayer(request.sender());
        String requesterName = requester != null ? requester.getName() : "Offline player";

        // Both request types need a live sender: TARGET_HERE later reads the requester's
        // location, which is null after they quit, and the service would report failure
        // after both players had already been told the request was accepted.
        if (requester == null) {
            player.sendMessage(messages.get("request-sender-offline"));
            return;
        }

        if (requester != null) {
            requester.sendMessage(messages.get("request-accepted", "player", player.getName()));
        }
        player.sendMessage(messages.get("request-accepted-self", "player", requesterName));

        if (request.type() == RequestManager.Type.TO_TARGET) {
            service.begin(requester, () -> locationOf(player.getUniqueId()));
        } else {
            service.begin(player, () -> locationOf(request.sender()));
        }
    }

    private void deny(Player player) {
        Messages messages = module.messages();
        Optional<RequestManager.Request> taken =
                module.requests().takeFor(player.getUniqueId(), System.currentTimeMillis());
        if (taken.isEmpty()) {
            player.sendMessage(messages.get("request-none"));
            return;
        }
        Player requester = Bukkit.getPlayer(taken.get().sender());
        String requesterName = requester != null ? requester.getName() : "Offline player";
        if (requester != null) {
            requester.sendMessage(messages.get("request-denied", "player", player.getName()));
        }
        player.sendMessage(messages.get("request-denied-self", "player", requesterName));
    }

    private void cancel(Player player) {
        Messages messages = module.messages();
        boolean removed = module.requests().cancelFrom(player.getUniqueId());
        player.sendMessage(messages.get(removed ? "request-cancelled" : "request-none"));
    }

    private void tphere(Player player, String[] args) {
        Messages messages = module.messages();
        if (args.length < 1) {
            player.sendMessage(messages.get("usage", "usage", "/tphere <player>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(messages.get("player-not-found", "player", args[0]));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(messages.get("self-target"));
            return;
        }
        service.teleportNow(target, player.getLocation());
        player.sendMessage(messages.get("tphere-done", "player", target.getName()));
    }

    private void back(Player player) {
        Location location = service.backLocation(player);
        if (location == null) {
            player.sendMessage(module.messages().get("back-none"));
            return;
        }
        service.begin(player, () -> location);
    }

    private void spawn(Player player) {
        Optional<Location> spawn = module.storage().spawn();
        if (spawn.isEmpty()) {
            player.sendMessage(module.messages().get("spawn-not-set"));
            return;
        }
        Location location = spawn.get();
        service.begin(player, () -> location);
    }

    private void setSpawn(Player player) {
        module.storage().setSpawn(player.getLocation());
        player.sendMessage(module.messages().get("spawn-set"));
    }

    private static Location locationOf(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? null : player.getLocation();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("rtp")) {
            return module.rtp().command().tabComplete(sender, args);
        }
        if (args.length != 1 || !(name.equals("tpa") || name.equals("tpahere") || name.equals("tphere"))) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender)) {
                continue;
            }
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(online.getName());
            }
        }
        return out;
    }
}
