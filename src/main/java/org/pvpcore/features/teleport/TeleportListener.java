package org.pvpcore.features.teleport;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Cancels warmups on move or damage, saves the death location for /back, cleans up on quit. */
public final class TeleportListener implements Listener {

    private final TeleportModule module;
    private final TeleportService service;

    public TeleportListener(TeleportModule module, TeleportService service) {
        this.module = module;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!service.hasWarmups() || !event.hasChangedBlock()
                || !module.settings().cancelWarmupOnMove()) {
            return;
        }
        service.checkMoved(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (service.hasWarmups() && event.getEntity() instanceof Player player) {
            service.cancelWarmup(player.getUniqueId(), "warmup-cancelled-damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        service.recordBack(event.getEntity(), event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!module.settings().enabled()) {
            return;
        }
        if (module.settings().respectBedAndAnchor()
                && (event.isBedSpawn() || event.isAnchorSpawn())) {
            return;
        }
        module.storage().spawn().ifPresent(event::setRespawnLocation);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.requests().removeInvolving(event.getPlayer().getUniqueId());
        service.forget(event.getPlayer().getUniqueId());
    }
}