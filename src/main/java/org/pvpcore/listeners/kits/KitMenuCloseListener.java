package org.pvpcore.listeners.kits;

import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.gui.kits.editor.EditorSaver;
import org.pvpcore.gui.kits.GUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * Saves a kit/enderchest editor when its inventory closes. Click permission checks now live in
 * the IF screens themselves (see GuiGuard and the per-screen click handlers).
 */
public class KitMenuCloseListener implements Listener {

    private final KitsContext kits;

    public KitMenuCloseListener(KitsContext kits) {
        this.kits = kits;
    }

    @EventHandler
    public void onEditorClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (inv.getLocation() != null) {
            return;
        }

        Player player = (Player) e.getPlayer();
        GUI.EditorContext context = GUI.getAndRemoveEditorContext(player.getUniqueId());
        if (context == null || inv.getSize() != context.type().topSize()) {
            return;
        }

        if (kits == null) {
            return;
        }
        EditorSaver.save(kits, player, context, inv);
    }
}