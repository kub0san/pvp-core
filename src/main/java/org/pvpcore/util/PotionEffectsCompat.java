package org.pvpcore.util;

import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public class PotionEffectsCompat {

    // Changed from ItemStack to PotionMeta to match ItemFilter.java
    public static List<PotionEffect> baseEffects(PotionMeta meta) {
        if (meta == null) {
            return List.of();
        }

        if (meta.getBasePotionType() == null) {
            return List.of();
        }

        // In 1.21, PotionType directly gives us the list of effects
        return meta.getBasePotionType().getPotionEffects();
    }
}