package org.pvpcore.listeners.kits;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.pvpcore.gui.kits.GUI;

/**
 * Keeps items inside an open kit/ender-chest editor.
 *
 * <p>The editor discards items through {@code DROP}/{@code CONTROL_DROP} clicks, which the
 * screen cancels and applies as a raw-slot removal. A {@link PlayerDropItemEvent} is a separate
 * event that would still spawn an {@code ItemEntity}; cancelling it while an editor context is
 * present guarantees no world drop can escape the editor.</p>
 */
public final class KitEditorDropGuard implements Listener {
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (GUI.peekEditorContext(player) != null) {
            event.setCancelled(true);
        }
    }
}
