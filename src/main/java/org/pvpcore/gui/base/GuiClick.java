package org.pvpcore.gui.base;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * A click on a button. The clicking player is already resolved, so a handler
 * never has to cast the event's clicker itself.
 *
 * <p>It lives in {@code org.pvpcore.gui.base} on purpose: it is the one piece of
 * the old {@code LiveScreen} contract that both the screen foundation and the
 * standalone pages need, and it has no dependency on either.</p>
 */
@FunctionalInterface
public interface GuiClick {
    void on(Player player, InventoryClickEvent event);
}
