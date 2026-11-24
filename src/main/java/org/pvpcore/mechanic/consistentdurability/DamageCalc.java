package org.pvpcore.mechanic.consistentdurability;

/*
 * Ported from ConsistentDurability (https://github.com/mcpvp-club/ConsistentDurability).
 * Licensed under GPL-3.0.
 */

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class DamageCalc {

    private DamageCalc() {
    }

    /** Chance a tool-like item takes damage, as a fraction. */
    static double calculateToolDamage(double originalDamage, int unbreakingLevel) {
        return originalDamage * (1.0d / (unbreakingLevel + 1));
    }

    /** Chance an armour item takes damage, as a fraction. */
    static double calculateArmorDamage(double originalDamage, int unbreakingLevel) {
        return originalDamage * (0.6d + 0.4d / (unbreakingLevel + 1));
    }

    /**
     * Reads the fractional durability stored in the PDC, falling back to the
     * integer damage component when the item has never been processed before.
     */
    static double getCurrentDurability(ItemStack item, int maxDurability) {
        Double currentDurability = item.getPersistentDataContainer()
                .get(ConsistentDurabilityMechanic.DURABILITY_KEY, PersistentDataType.DOUBLE);
        if (currentDurability == null) {
            int currentDamage = item.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
            currentDurability = (double) (maxDurability - currentDamage);
        }
        return currentDurability;
    }
}