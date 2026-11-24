package org.pvpcore.commands.kits.impl.features;

import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.Kits;
import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.commands.kits.impl.core.ActionGuards;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.util.kits.BroadcastManager;
import org.pvpcore.util.CooldownManager;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.StyleManager;
import org.pvpcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class RegearCommand implements CommandExecutor, Listener {

    private static final String MODE_HYBRID = "hybrid";
    private static final String MODE_SHULKER = "shulker";

    private final Plugin plugin;
    private final NamespacedKey regearModeKey;
    private final CooldownManager commandCooldownManager;
    private final CooldownManager damageCooldownManager;
    private final boolean allowRegearWhileUsingElytra;
    private final boolean preventPuttingItemsInRegearInventory;
    private final ItemStack regearShulkerItem;
    private final ItemStack regearShellItem;

    public RegearCommand(Plugin plugin) {
        this.plugin = plugin;
        this.regearModeKey = new NamespacedKey(plugin, "regear-mode");
        FileConfiguration cfg = (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().config() : null) != null ? (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().config() : null) : plugin.getConfig();
        int commandCooldownInSeconds = cfg.getInt("regear.command-cooldown-seconds", 5);
        int damageCooldownInSeconds = cfg.getInt("regear.damage-cooldown-seconds", 5);
        this.commandCooldownManager = new CooldownManager(commandCooldownInSeconds);
        this.damageCooldownManager = new CooldownManager(damageCooldownInSeconds);
        this.allowRegearWhileUsingElytra = cfg.getBoolean("regear.allow-while-using-elytra", true);
        this.preventPuttingItemsInRegearInventory = cfg.getBoolean("regear.prevent-putting-items-in-regear-inventory", false);

        String primaryTag = (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().styleManager() : null).getPrimaryColorTag();
        this.regearShulkerItem = GuiTheme.custom(
                Material.WHITE_SHULKER_BOX,
                primaryTag + org.pvpcore.commands.kits.KitsCommands.lang().raw("gui.regear-shulker-name"),
                org.pvpcore.commands.kits.KitsCommands.lang().raw("gui.lore-regear-restocks"),
                org.pvpcore.commands.kits.KitsCommands.lang().raw("gui.lore-regear-shulker-use", "primary", primaryTag)
        );
        this.regearShellItem = GuiTheme.custom(
                Material.SHULKER_SHELL,
                primaryTag + org.pvpcore.commands.kits.KitsCommands.lang().raw("gui.regear-shell-name"),
                org.pvpcore.commands.kits.KitsCommands.lang().raw("gui.lore-regear-restocks"),
                org.pvpcore.commands.kits.KitsCommands.lang().raw("gui.lore-regear-shell-click")
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTakesDamage(EntityDamageEvent event) {
        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return; // kits mechanics inert
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        damageCooldownManager.setCooldown(player);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayerAtLocation(sender, LocationFeature.REGEAR);
        if (player == null) {
            return true;
        }

        String effectiveMode = getEffectiveMode(label);
        if (effectiveMode.equalsIgnoreCase("shulker")) {
            handleShulkerMode(player, false);
            return true;
        }

        if (effectiveMode.equalsIgnoreCase("hybrid")) {
            handleShulkerMode(player, true);
            return true;
        }

        if (effectiveMode.equalsIgnoreCase("command")) {
            handleCommandMode(player);
            return true;
        }

        org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.regear-misconfigured");
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onShulkerPlace(BlockPlaceEvent event) {
        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return; // regearing via shulker place is a kits mechanic
        }
        ItemStack shulker = event.getItemInHand();
        String mode = regearMode(shulker);
        if (plugin.getConfig().getBoolean("debug", false)) {
            ItemMeta meta = shulker.getItemMeta();
            String pdc = meta == null ? "no-meta" : String.valueOf(meta.getPersistentDataContainer().get(regearModeKey, PersistentDataType.STRING));
            plugin.getLogger().warning("[REGEAR-DEBUG] place item=" + shulker.getType() + " pdcMode=" + pdc + " resolvedMode=" + mode);
        }
        if (mode == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        // The mode lives on the item, not a process-lifetime map: rejected placements cannot leave
        // stale state, and duplicate/lost shulkers behave exactly like the one that was handed out.
        boolean allowed = canRegear(player, false);
        if (plugin.getConfig().getBoolean("debug", false) && !allowed) {
            plugin.getLogger().warning("[REGEAR-DEBUG] placement rejected by eligibility checks (permission/last kit/elytra/damage cooldown)");
        }
        if (!allowed) {
            return;
        }

        ItemStack consumed = shulker.clone();
        player.getInventory().setItem(event.getHand(), null);

        if (MODE_HYBRID.equals(mode)) {
            // Hybrid: placing the shulker regears directly, without opening the GUI.
            if (!org.pvpcore.commands.kits.KitsCommands.km().regearLastKit(player)) {
                plugin.getLogger().warning("[REGEAR-DEBUG] regearLastKit returned false for " + player.getName());
                player.getInventory().addItem(consumed);
                org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.regear-failed");
                return;
            }
            player.updateInventory();
            announceRegearSuccess(player);
            return;
        }

        RegearInventoryHolder holder = new RegearInventoryHolder(player);
        Inventory inventory = createRegearInventory(holder);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onShulkerShellClick(InventoryClickEvent event) {
        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return; // the regear inventory is a kits mechanic
        }
        if (!(event.getInventory().getHolder() instanceof RegearInventoryHolder holder)) {
            return;
        }
        if (preventPuttingItemsInRegearInventory) event.setCancelled(true);
        if (!event.getWhoClicked().getUniqueId().equals(holder.player().getUniqueId())) { event.setCancelled(true); return; }
        ItemStack currentItem = event.getCurrentItem();

        if (currentItem == null) {
            return;
        }

        if (!currentItem.equals(regearShellItem)) {
            if (preventPuttingItemsInRegearInventory) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        Player player = holder.player();

        if (!canRegear(player, false)) {
            return;
        }

        player.closeInventory();

        if (!org.pvpcore.commands.kits.KitsCommands.km().regearLastKit(player)) return;
        player.updateInventory();

        announceRegearSuccess(player);
    }

    @EventHandler
    public void onShulkerDrag(InventoryDragEvent event) {
        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return;
        }
        if (preventPuttingItemsInRegearInventory && event.getInventory().getHolder() instanceof RegearInventoryHolder
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getInventory().getSize())) event.setCancelled(true);
    }

    private String getEffectiveMode(String label) {
        if (label != null && label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        FileConfiguration cfg = (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().config() : null) != null ? (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().config() : null) : plugin.getConfig();
        String mode;
        if (label != null && label.equalsIgnoreCase("rg")) {
            mode = cfg.getString("regear.modes.rg", "command");
        } else if (label != null && label.equalsIgnoreCase("regear")) {
            mode = cfg.getString("regear.modes.regear", "command");
        } else {
            mode = cfg.getString("regear.modes.rg", "command");
        }
        if (mode == null) {
            mode = "command";
        }
        mode = mode.toLowerCase(Locale.ROOT);
        if (mode.equals("hybrid") || mode.equals("shulker") || mode.equals("command")) {
            return mode;
        }
        return "command";
    }

    private void handleShulkerMode(Player player, boolean hybrid) {
        if (hasRegearShulker(player)) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.regear-shulker-already-held");
            return;
        }
        int slot = player.getInventory().firstEmpty();
        if (slot == -1) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.inventory-full");
            return;
        }

        player.getInventory().setItem(slot, taggedShulker(hybrid ? MODE_HYBRID : MODE_SHULKER));
        org.pvpcore.commands.kits.KitsCommands.lang().send(player, "success.shulker-given");
    }

    private boolean canRegear(Player player, boolean checkCommandCooldown) {
        if (!ActionGuards.allowed(player, "perplayerkit.regear")) return false;
        if (!hasLastKit(player)) return false;
        if (isElytraBlocked(player)) return false;
        if (isDamageCooldownBlocked(player)) return false;
        return !checkCommandCooldown || !isCommandCooldownBlocked(player);
    }

    private ItemStack taggedShulker(String mode) {
        ItemStack item = regearShulkerItem.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(regearModeKey, PersistentDataType.STRING, mode);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Reads the regear mode embedded in the shulker, or null for a non-regear item. */
    private String regearMode(ItemStack item) {
        if (item == null || item.getType() != Material.WHITE_SHULKER_BOX) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String mode = meta.getPersistentDataContainer().get(regearModeKey, PersistentDataType.STRING);
        if (MODE_HYBRID.equals(mode) || MODE_SHULKER.equals(mode)) return mode;
        // Legacy shulkers handed out before item tags have the same display name. Treat them as
        // shulker mode so they still open the GUI rather than silently doing nothing.
        ItemMeta templateMeta = regearShulkerItem.getItemMeta();
        if (templateMeta != null && templateMeta.displayName() != null
                && templateMeta.displayName().equals(meta.displayName())) {
            return MODE_SHULKER;
        }
        return null;
    }

    private boolean hasRegearShulker(Player player) {
        for (ItemStack held : player.getInventory().getContents()) {
            if (regearMode(held) != null) return true;
        }
        return false;
    }

    private void handleCommandMode(Player player) {
        if (!canRegear(player, true)) {
            return;
        }

        if (!org.pvpcore.commands.kits.KitsCommands.km().regearLastKit(player)) return;
        announceRegearSuccess(player);
        commandCooldownManager.setCooldown(player);
    }

    private boolean hasLastKit(Player player) {
        if (!ActionGuards.dataReady(player)) return false;
        if (org.pvpcore.commands.kits.KitsCommands.km().hasLastKit(player.getUniqueId())) return true;
        org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.no-kit-loaded");
        return false;
    }

    private boolean isElytraBlocked(Player player) {
        if (allowRegearWhileUsingElytra) {
            return false;
        }
        if (!player.isGliding()) {
            return false;
        }
        if (player.getInventory().getChestplate() == null || player.getInventory().getChestplate().getType() != Material.ELYTRA) {
            return false;
        }

        org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.regear-elytra-blocked");
        return true;
    }

    private boolean isDamageCooldownBlocked(Player player) {
        if (!damageCooldownManager.isOnCooldown(player)) {
            return false;
        }

        int secondsLeft = damageCooldownManager.getTimeLeft(player);
        org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.regear-combat-cooldown", "seconds", String.valueOf(secondsLeft));
        return true;
    }

    private boolean isCommandCooldownBlocked(Player player) {
        if (!commandCooldownManager.isOnCooldown(player)) {
            return false;
        }

        int secondsLeft = commandCooldownManager.getTimeLeft(player);
        org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.regear-command-cooldown", "seconds", String.valueOf(secondsLeft));
        return true;
    }

    private void announceRegearSuccess(Player player) {
        org.pvpcore.commands.kits.KitsCommands.lang().send(player, "success.regeared");
        (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().broadcastManager() : null).broadcastPlayerRegeared(player);
    }

    private Inventory createRegearInventory(RegearInventoryHolder holder) {
        // The String overload of createInventory is deprecated; the Component one
        // is used instead. The title is built as a legacy string and read back
        // with the hex-aware serializer, so the colours stay intact.
        Inventory inventory = Bukkit.createInventory(holder, 27,
                TextUtil.LEGACY.deserialize(
                        (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().styleManager() : null).getPrimaryColor() + org.pvpcore.commands.kits.KitsCommands.lang().legacy("gui.regear-shulker-title")));
        inventory.setItem(13, regearShellItem);
        holder.setInventory(inventory);
        return inventory;
    }

    public static class RegearInventoryHolder implements InventoryHolder {
        private final Player player;
        private Inventory inventory;

        public RegearInventoryHolder(Player player) {
            this.player = player;
            this.inventory = Bukkit.createInventory(this, 27);
        }

        public Player player() {
            return player;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
