package org.pvpcore.mechanic.totemfixes;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Corrects the client inventory after totem-related inventory operations.
 * Updates are coalesced to at most one task per player per tick.
 */
final class TotemInventorySync implements Listener {

    private final Plugin plugin;
    private final TotemSwapTracker tracker;
    private final Map<UUID, BukkitTask> scheduledUpdates = new HashMap<>();

    TotemInventorySync(Plugin plugin, TotemSwapTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        TotemSwapTracker.Action action = event instanceof InventoryCreativeEvent
                ? TotemSwapTracker.Action.CREATIVE_ACTION
                : TotemSwapTracker.Action.INVENTORY_CLICK;
        tracker.eventProcessed(player.getUniqueId(), action);

        if (!event.isCancelled() && clickInvolvesTotem(event, player)) {
            scheduleUpdate(player);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        tracker.eventProcessed(player.getUniqueId(), TotemSwapTracker.Action.INVENTORY_CLICK);
        if (event.isCancelled()) {
            return;
        }

        boolean hasTotem = isTotem(event.getOldCursor());
        if (!hasTotem) {
            hasTotem = event.getNewItems().values().stream().anyMatch(TotemInventorySync::isTotem);
        }
        if (hasTotem) {
            scheduleUpdate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        tracker.eventProcessed(player.getUniqueId(), TotemSwapTracker.Action.HAND_SWAP);

        if (!event.isCancelled()
                && (isTotem(event.getMainHandItem()) || isTotem(event.getOffHandItem()))) {
            scheduleUpdate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        tracker.eventProcessed(player.getUniqueId(), TotemSwapTracker.Action.HOTBAR_CHANGE);

        if (event.isCancelled()) {
            return;
        }

        ItemStack selected = player.getInventory().getItem(event.getNewSlot());
        if (isTotem(selected) || isTotem(player.getInventory().getItemInOffHand())) {
            scheduleUpdate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.isCancelled()) {
            return;
        }
        scheduleUpdate(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancelUpdate(event.getPlayer().getUniqueId());
        tracker.remove(event.getPlayer().getUniqueId());
    }

    private boolean clickInvolvesTotem(InventoryClickEvent event, Player player) {
        if (isTotem(event.getCurrentItem()) || isTotem(event.getCursor())) {
            return true;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            return hotbarButton >= 0
                    && isTotem(player.getInventory().getItem(hotbarButton));
        }

        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return isTotem(player.getInventory().getItemInOffHand());
        }

        return false;
    }

    private void scheduleUpdate(Player player) {
        UUID playerId = player.getUniqueId();
        if (scheduledUpdates.containsKey(playerId)) {
            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scheduledUpdates.remove(playerId);
            if (player.isOnline()) {
                player.updateInventory();
            }
        }, 1L);
        scheduledUpdates.put(playerId, task);
    }

    private void cancelUpdate(UUID playerId) {
        BukkitTask task = scheduledUpdates.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    void close() {
        for (BukkitTask task : scheduledUpdates.values()) {
            task.cancel();
        }
        scheduledUpdates.clear();
    }

    private static boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING && item.getAmount() > 0;
    }
}
