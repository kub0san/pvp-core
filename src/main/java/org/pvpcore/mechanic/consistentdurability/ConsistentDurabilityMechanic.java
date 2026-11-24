package org.pvpcore.mechanic.consistentdurability;

/*
 * Ported from ConsistentDurability (https://github.com/mcpvp-club/ConsistentDurability).
 * Licensed under GPL-3.0.
 *
 * Only the Unbreaking RNG removal is implemented. The per-tick elytra durability
 * decay from the original plugin is intentionally not ported.
 */

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.mechanic.Mechanic;

import java.util.Set;

/**
 * Consistent Durability: removes RNG from the Unbreaking enchantment on armour,
 * shields, and elytra by using the average expected value instead of a per-hit roll.
 *
 * <p>The mechanic keeps a fractional durability counter in the item's
 * {@link PersistentDataContainer} so that the average benefit is applied smoothly
 * across hits rather than being rounded to an integer every time.</p>
 *
 * <p>This is intentionally non-vanilla. It is off by default and is a global toggle
 * only.</p>
 */
public final class ConsistentDurabilityMechanic implements Mechanic, Listener {

    /** Matches the key used by the standalone ConsistentDurability plugin. */
    static final NamespacedKey DURABILITY_KEY =
            new NamespacedKey("consistentdurability", "durability");

    private PvPCorePlugin plugin;
    private volatile boolean active;

    @Override
    public String id() {
        return "consistentdurability";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("cdurability");
    }

    @Override
    public String displayName() {
        return "Consistent Durability";
    }

    @Override
    public String description() {
        return "Removes RNG from Unbreaking on armour, shields, and elytra.";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        this.plugin = plugin;
        try {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            active = true;
            plugin.getLogger().info(
                    "Consistent Durability enabled: Unbreaking uses the average expected value.");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe(
                    "Consistent Durability could not register its listener: " + t);
            return false;
        }
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!active) {
            return;
        }
        HandlerList.unregisterAll(this);
        active = false;
        plugin.getLogger().info("Consistent Durability disabled.");
    }

    @Override
    public String statusNote() {
        return active ? "average Unbreaking applied to armour, shields, and elytra" : null;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        if (!active) {
            return;
        }

        ItemStack item = event.getItem();
        boolean isElytra = item.getType() == Material.ELYTRA;
        boolean isShield = item.getType() == Material.SHIELD;

        if (!Tag.ITEMS_ENCHANTABLE_ARMOR.isTagged(item.getType())
                && !isElytra && !isShield) {
            return;
        }

        int level = item.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (level <= 0) {
            return;
        }

        Integer maxDurability = item.getData(DataComponentTypes.MAX_DAMAGE);
        if (maxDurability == null) {
            return;
        }

        int originalDamage = event.getOriginalDamage();
        if (originalDamage <= 0) {
            return;
        }

        int currentRawDamage = item.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        int currentRawDurability = maxDurability - currentRawDamage;

        double damage = (isElytra || isShield)
                ? DamageCalc.calculateToolDamage(originalDamage, level)
                : DamageCalc.calculateArmorDamage(originalDamage, level);

        double currentDurability = DamageCalc.getCurrentDurability(item, maxDurability);
        double newDurability = currentDurability - damage;

        if (newDurability < 1E-7) {
            event.setDamage(maxDurability);
        } else {
            event.setDamage(currentRawDurability - (int) Math.ceil(newDurability));
            item.editMeta(meta -> {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(DURABILITY_KEY, PersistentDataType.DOUBLE, newDurability);
            });
        }
    }

    /**
     * Keeps the fractional durability counter in sync with Mending repairs.
     *
     * <p>Mending lowers the item's {@code DAMAGE} component (i.e. raises its real
     * durability) but leaves the PDC value untouched. Without this handler the next
     * damage event reads a stale PDC value and the item appears to revert to its
     * pre-mending durability. Running at MONITOR with {@code ignoreCancelled} means
     * we only apply the delta when the repair actually happened.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemMend(PlayerItemMendEvent event) {
        if (!active) {
            return;
        }

        ItemStack item = event.getItem();
        boolean isElytra = item.getType() == Material.ELYTRA;
        boolean isShield = item.getType() == Material.SHIELD;
        if (!Tag.ITEMS_ENCHANTABLE_ARMOR.isTagged(item.getType())
                && !isElytra && !isShield) {
            return;
        }

        int repair = event.getRepairAmount();
        if (repair <= 0) {
            return;
        }

        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            Double current = pdc.get(DURABILITY_KEY, PersistentDataType.DOUBLE);
            // If the item has never been damaged through this mechanic, the PDC has no
            // entry yet. Leave it absent so the first damage event still initialises
            // from the DAMAGE component (which is now correct after the mend).
            if (current != null) {
                pdc.set(DURABILITY_KEY, PersistentDataType.DOUBLE, current + repair);
            }
        });
    }
}