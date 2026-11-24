package org.pvpcore.mechanic.fastercrystals;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before the Faster Crystals packet path spawns an end crystal.
 *
 * <p>This is PvPCore's protection hook for the accelerated placement path. It is followed by
 * a synthetic {@link org.bukkit.event.entity.EntityPlaceEvent} after the entity exists, so
 * plugins that listen for Bukkit's normal placement event can cancel the result as well.</p>
 */
public final class FastCrystalPlaceEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location location;
    private final ItemStack item;
    private boolean cancelled;

    public FastCrystalPlaceEvent(@NotNull Player player, @NotNull Location location,
                                 @NotNull ItemStack item) {
        super(player);
        this.location = location.clone();
        this.item = item;
    }

    public @NotNull Location getLocation() {
        return location.clone();
    }

    public @NotNull ItemStack getItem() {
        return item;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
