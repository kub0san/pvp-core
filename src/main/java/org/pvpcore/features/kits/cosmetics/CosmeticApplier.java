package org.pvpcore.features.kits.cosmetics;

import net.kyori.adventure.text.Component;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.pvpcore.config.kits.CustomizeRules;

import java.util.Objects;
import java.util.function.Predicate;

/** Applies validated cosmetics to clones of kit items; the source item is never mutated. */
public final class CosmeticApplier {
    private final CustomizeRules rules;
    private final CosmeticValidator validator;

    public CosmeticApplier(CustomizeRules rules, CosmeticValidator validator) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public ItemStack applyTrim(ItemStack item, TrimMaterial material, TrimPattern pattern) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(pattern, "pattern");
        if (!allows(keyOf(material), rules::allowsTrimMaterial)) {
            throw new IllegalArgumentException("Trim material is not allowed");
        }
        if (!allows(keyOf(pattern), rules::allowsTrimPattern)) {
            throw new IllegalArgumentException("Trim pattern is not allowed");
        }
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (!(meta instanceof ArmorMeta armor)) {
            throw new IllegalArgumentException("Trim target must be armor");
        }
        armor.setTrim(new ArmorTrim(material, pattern));
        result.setItemMeta(armor);
        return result;
    }

    public ItemStack removeTrim(ItemStack item) {
        Objects.requireNonNull(item, "item");
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (!(meta instanceof ArmorMeta armor)) {
            throw new IllegalArgumentException("Trim target must be armor");
        }
        armor.setTrim(null);
        result.setItemMeta(armor);
        return result;
    }

    public ItemStack applyName(ItemStack item, String raw) {
        Objects.requireNonNull(item, "item");
        Component name = validator.parseName(raw);
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("Item has no metadata");
        }
        meta.displayName(name);
        result.setItemMeta(meta);
        return result;
    }

    /** Keyless/inline registry entries are preserved rather than rejected. */
    private static String keyOf(Keyed keyed) {
        try {
            NamespacedKey key = keyed.getKey();
            return key == null ? null : key.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean allows(String id, Predicate<String> allowlist) {
        return id == null || allowlist.test(id);
    }
}
