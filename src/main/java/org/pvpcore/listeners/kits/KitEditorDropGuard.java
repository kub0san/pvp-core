package org.pvpcore.listeners.kits;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.kits.editor.EditorHintLore;

/**
 * Keeps items inside an open kit/ender-chest editor.
 *
 * <p>The editor discards items through {@code DROP}/{@code CONTROL_DROP} clicks, which the
 * screen cancels and applies as a raw-slot removal. A {@link PlayerDropItemEvent} is a separate
 * event that would still spawn an {@code ItemEntity}; cancelling it while an editor context is
 * present guarantees no world drop can escape the editor. The display clones also carry
 * hover-only lore marked by {@link EditorHintLore}; every exit path strips that marker so the
 * hints never travel with an item that leaves the editor.</p>
 */
public final class KitEditorDropGuard implements Listener {
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (GUI.peekEditorContext(player) == null) {
            return;
        }
        Item dropped = event.getItemDrop();
        if (dropped != null) {
            ItemStack stack = dropped.getItemStack();
            EditorHintLore.strip(stack);
            dropped.setItemStack(stack);
        }
        event.setCancelled(true);
    }

    /**
     * MONITOR runs after the editor's own click handling, so a display clone picked up by a
     * click has its hint lore cleaned before the player can carry it out of the editor.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (GUI.peekEditorContext(player) == null) {
            return;
        }
        ItemStack cursor = event.getCursor();
        EditorHintLore.strip(cursor);
        event.getView().setCursor(cursor);
    }

    /**
     * MONITOR runs after the drag has been applied, so display clones dropped into any slot are
     * cleaned before the player can pick them up and carry them out of the editor.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (GUI.peekEditorContext(player) == null) {
            return;
        }
        InventoryView view = event.getView();
        ItemStack cursor = event.getCursor();
        EditorHintLore.strip(cursor);
        view.setCursor(cursor);

        for (int raw : event.getRawSlots()) {
            Inventory inventory = view.getInventory(raw);
            if (inventory == null) {
                continue;
            }
            int slot = view.convertSlot(raw);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            EditorHintLore.strip(item);
            inventory.setItem(slot, item);
        }
    }

    /**
     * LOW runs before the editor close/save listeners consume the context (see
     * {@code KitMenuCloseListener} and {@code ItemEditorPage}). The hint lore is stripped from
     * everything the player is carrying, not from the editor inventory being saved.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (GUI.peekEditorContext(player) == null) {
            return;
        }
        stripCarriedHints(player);
    }

    /** LOW runs before {@code QuitListener} forgets the editor context. */
    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (GUI.peekEditorContext(player) == null) {
            return;
        }
        stripCarriedHints(player);
    }

    /** Strips marked items on the cursor, in the inventory and in the offhand slot. */
    private static void stripCarriedHints(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        EditorHintLore.strip(cursor);
        player.setItemOnCursor(cursor);

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            EditorHintLore.strip(item);
            inventory.setItem(i, item);
        }

        ItemStack offhand = inventory.getItemInOffHand();
        EditorHintLore.strip(offhand);
        inventory.setItemInOffHand(offhand);
    }
}
