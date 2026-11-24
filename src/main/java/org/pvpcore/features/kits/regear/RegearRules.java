package org.pvpcore.features.kits.regear;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Internal regear configuration: legacy material whitelist plus optional meta-aware rules.
 * Parsed on every regear so {@code /pvpcore reload} is honoured without static state.
 */
public final class RegearRules {

    public enum OverflowAction { DROP, ENDERCHEST, BLOCK }

    /**
     * A matcher. Every configured criterion must match; unspecified criteria are ignored.
     * Enchant levels are exact, so a rule can target (for example) only a Sharpness V sword.
     */
    public record Rule(Material material, Map<Enchantment, Integer> enchantments,
                       Boolean unbreakable, String potionType) {

        public boolean matches(ItemStack item) {
            if (item == null || item.getType() != material) return false;
            if (!enchantments.isEmpty()) {
                Map<Enchantment, Integer> actual = item.getEnchantments();
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    if (!entry.getValue().equals(actual.get(entry.getKey()))) return false;
                }
            }
            ItemMeta meta = item.getItemMeta();
            if (unbreakable != null) {
                if (meta == null || meta.isUnbreakable() != unbreakable) return false;
            }
            if (potionType != null) {
                if (!(meta instanceof PotionMeta potion) || potion.getBasePotionType() == null
                        || !potionType.equals(potion.getBasePotionType().getKey().toString())) {
                    return false;
                }
            }
            return true;
        }
    }

    private final boolean invert;
    private final boolean restoreExact;
    private final boolean restoreArmor;
    private final boolean restoreOffhand;
    private final OverflowAction overflowAction;
    private final List<Rule> rules;

    private RegearRules(boolean invert, boolean restoreExact, boolean restoreArmor, boolean restoreOffhand,
                        OverflowAction overflowAction, List<Rule> rules) {
        this.invert = invert;
        this.restoreExact = restoreExact;
        this.restoreArmor = restoreArmor;
        this.restoreOffhand = restoreOffhand;
        this.overflowAction = overflowAction;
        this.rules = List.copyOf(rules);
    }

    public static RegearRules parse(FileConfiguration config) {
        boolean invert = config.getBoolean("regear.invert-whitelist", false);
        boolean restoreExact = config.getBoolean("regear.restore-exact-positions", true);
        boolean restoreArmor = config.getBoolean("regear.restore-armor", false);
        boolean restoreOffhand = config.getBoolean("regear.restore-offhand", false);

        OverflowAction overflow = OverflowAction.DROP;
        String configured = config.getString("regear.overflow.action", "drop");
        if (configured != null) {
            try {
                overflow = OverflowAction.valueOf(configured.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Unknown value falls back to the safe default (drop) instead of deleting items.
            }
        }

        List<Rule> parsed = new ArrayList<>();
        ConfigurationSection rulesSection = config.getConfigurationSection("regear.rules");
        if (rulesSection != null) {
            for (String name : rulesSection.getKeys(false)) {
                Material material = Material.matchMaterial(name.trim());
                if (material == null || !material.isItem() || material.isAir()) {
                    continue; // Fail closed: an invalid rule never matches anything.
                }
                ConfigurationSection entry = rulesSection.getConfigurationSection(name);
                if (entry == null) continue;

                Map<Enchantment, Integer> enchantments = new HashMap<>();
                ConfigurationSection enchants = entry.getConfigurationSection("enchantments");
                if (enchants != null) {
                    for (String key : enchants.getKeys(false)) {
                        NamespacedKey id = NamespacedKey.fromString(key.trim().toLowerCase(Locale.ROOT));
                        Enchantment enchant = id == null ? null : Enchantment.getByKey(id);
                        int level = enchants.getInt(key, 0);
                        if (enchant != null && level > 0) enchantments.put(enchant, level);
                    }
                }
                Boolean unbreakable = entry.contains("unbreakable") && entry.isBoolean("unbreakable")
                        ? entry.getBoolean("unbreakable") : null;
                String potionType = entry.contains("potion-type") ? entry.getString("potion-type") : null;
                if (potionType != null) potionType = potionType.trim().toLowerCase(Locale.ROOT);
                parsed.add(new Rule(material, Map.copyOf(enchantments), unbreakable, potionType));
            }
        }
        // Legacy entries stay material-only and merge with the meta-aware rules.
        for (String name : config.getStringList("regear.whitelist")) {
            Material material = Material.matchMaterial(name.trim());
            if (material != null && material.isItem() && !material.isAir()) {
                parsed.add(new Rule(material, Map.of(), null, null));
            }
        }
        return new RegearRules(invert, restoreExact, restoreArmor, restoreOffhand, overflow, parsed);
    }

    /** True when the item matches any configured rule, ignoring the inverted/whitelist mode. */
    public boolean matches(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        for (Rule rule : rules) {
            if (rule.matches(item)) return true;
        }
        return false;
    }

    /** True when the item must NOT be restocked by regear. */
    public boolean excluded(ItemStack item) {
        return invert ? matches(item) : !matches(item);
    }

    public boolean restoreExact() { return restoreExact; }
    public boolean restoreArmor() { return restoreArmor; }
    public boolean restoreOffhand() { return restoreOffhand; }
    public OverflowAction overflowAction() { return overflowAction; }
}
