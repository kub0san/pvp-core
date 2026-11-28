package org.pvpcore.features.teleport.rtp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Removes queue entries when a player leaves the server. Death and world changes are ignored. */
public final class RtpQueueListener implements Listener {

    private final RtpQueue queue;

    public RtpQueueListener(RtpQueue queue) {
        this.queue = queue;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        queue.leave(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        queue.leave(event.getPlayer().getUniqueId());
    }
}
