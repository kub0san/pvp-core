package org.pvpcore.features.teleport.rtp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.config.Messages;
import org.pvpcore.features.teleport.TeleportService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Runs one /rtpq match: recheck both players, acquire a single shared spot, recheck again at
 * teleport time, then hand each player their own warmup. Main thread only (as {@link RtpQueue}).
 * A failed match is cancelled for both players and never requeued.
 */
public final class RtpMatchmaker {

    private final JavaPlugin plugin;
    private final TeleportService teleportService;
    private final Supplier<RtpSettings> settings;
    private final Supplier<Messages> messages;
    private final BiFunction<RtpRegion, Player, CompletableFuture<Location>> acquire;

    public RtpMatchmaker(JavaPlugin plugin, TeleportService teleportService,
                         Supplier<RtpSettings> settings, Supplier<Messages> messages,
                         BiFunction<RtpRegion, Player, CompletableFuture<Location>> acquire) {
        this.plugin = plugin;
        this.teleportService = teleportService;
        this.settings = settings;
        this.messages = messages;
        this.acquire = acquire;
    }

    /** Called on the main thread by the queue's match sink. */
    public void startMatch(RtpMatch match) {
        RtpSettings current = settings.get();
        RtpRegion region = current == null ? null : current.region(match.regionId());
        if (region == null) {
            cancel(match);
            return;
        }
        Player first = Bukkit.getPlayer(match.first());
        Player second = Bukkit.getPlayer(match.second());
        if (!eligible(first, region) || !eligible(second, region)) {
            cancel(match);
            return;
        }

        // RtpService completes on the main thread; runOnMain keeps that guarantee even if a
        // different supplier is injected.
        acquire.apply(region, first).thenAccept(location -> runOnMain(() -> finish(match, location)));
    }

    private void finish(RtpMatch match, Location location) {
        if (location == null) {
            cancel(match);
            return;
        }
        // Both players and the region are re-read: a reload or a kick may have landed while
        // the async acquire was running.
        RtpSettings current = settings.get();
        RtpRegion region = current == null ? null : current.region(match.regionId());
        Player first = Bukkit.getPlayer(match.first());
        Player second = Bukkit.getPlayer(match.second());
        if (region == null || !eligible(first, region) || !eligible(second, region)) {
            cancel(match);
            return;
        }

        notify(first, second.getName(), match.regionId());
        notify(second, first.getName(), match.regionId());

        TeleportService.Options options = new TeleportService.Options(
                current.warmupSeconds(), current.cooldownSeconds(), "rtp-failed", "rtp-success");
        // Independent warmups: cancelling one player's does not touch the partner's.
        teleportService.begin(first, () -> location.clone(), options);
        teleportService.begin(second, () -> location.clone(), options);
    }

    private boolean eligible(Player player, RtpRegion region) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!player.hasPermission("pvpcore.teleportation.rtpq")) {
            return false;
        }
        if (!region.permission().isEmpty() && !player.hasPermission(region.permission())) {
            return false;
        }
        return !teleportService.hasPending(player) && teleportService.getRemainingCooldown(player) == 0;
    }

    private void notify(Player player, String partnerName, String regionId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Messages facade = messages.get();
        player.sendMessage(facade == null
                ? Component.text("rtpq-matched")
                : facade.get("rtpq-matched", "player", partnerName, "region", regionId));
    }

    private void cancel(RtpMatch match) {
        notifyFailed(match.first());
        notifyFailed(match.second());
    }

    private void notifyFailed(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            return;
        }
        Messages facade = messages.get();
        player.sendMessage(facade == null ? Component.text("rtp-failed") : facade.get("rtp-failed"));
    }

    private void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }
}
