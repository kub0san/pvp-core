/*
 * Copyright 2022-2025 Noah Ross
 *
 * This file is part of Kits.
 *
 * Kits is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Kits is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Kits. If not, see <https://www.gnu.org/licenses/>.
 */
package org.pvpcore.features.kits;

import org.pvpcore.config.kits.ItemFilterRules;
import org.pvpcore.util.PotionEffectsCompat;
import org.bukkit.entity.Player;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.pvpcore.PvPCorePlugin;

import java.util.*;

/** Applies one configured policy to copied items, including stored enchants and nested contents. */
public class ItemFilter {
    /** why: legacy static whitelist cache retained for external/legacy compatibility; instance filtering uses current kit-room whitelist state. */
    public static Set<String> whitelist = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int MAX_CONTAINER_DEPTH = 16; // why: recursion guard constant retained for nested container/bundle filtering safety.
    private final KitsContext context;
    private final ItemFilterRules.Settings settings;
    private final Plugin plugin;
    private final Set<String> warnings = new HashSet<>();

    public ItemFilter(KitsContext context) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.plugin = context.plugin();
        this.settings = ItemFilterRules.parse(context.config());
        whitelist.clear();
    }

    /** Test seam for policy queries without a live plugin context; no static state is touched. */
    ItemFilter(ItemFilterRules.Settings settings, Plugin plugin) {
        this.context = null;
        this.plugin = plugin;
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
    }

    public KitsContext context() { return context; }

    /** why: stateless item-array copy utility retained for external/legacy compatibility. */
    public static ItemStack[] copy(ItemStack[] input) { return KitContents.copy(input); }

    public boolean isReady() { return !settings.enabled() || !settings.kitRoomOnly() || !whitelist.isEmpty(); }
    public boolean filtersImports() { return settings.enabled() && settings.imports(); }
    public boolean filtersSaves() { return settings.enabled() && settings.saves(); }
    public ItemStack[] filterItemStack(ItemStack[] input) { return filter(input, 0); }

    /** Refuse an entirely filtered kit instead of clearing the destination inventory/editor. */
    public ItemStack[] filterKit(ItemStack[] input, Player recipient) {
        ItemStack[] output = filterItemStack(input);
        if (!KitContents.isEmpty(input) && KitContents.isEmpty(output)) {
            if (recipient != null && context != null && context.lang() != null) {
                context.lang().send(recipient, "error.item-filter-empty");
            }
            return null;
        }
        return output;
    }

    private ItemStack[] filter(ItemStack[] input, int depth) {
        ItemStack[] output = KitContents.copy(input);
        if (output == null || !settings.enabled()) return output;
        for (int i = 0; i < output.length; i++) {
            ItemStack item = output[i];
            if (item == null) continue;
            try {
                if (depth > MAX_CONTAINER_DEPTH || !allowed(item)) { output[i] = null; continue; }
                ItemMeta itemMeta = item.getItemMeta();
                if (itemMeta instanceof BlockStateMeta meta && meta.getBlockState() instanceof Container container) {
                    container.getInventory().setContents(filter(container.getInventory().getContents(), depth + 1));
                    meta.setBlockState(container); item.setItemMeta(meta);
                }
                if (itemMeta instanceof BundleMeta meta) {
                    ItemStack[] nested = filter(meta.getItems().toArray(new ItemStack[0]), depth + 1);
                    List<ItemStack> kept = new ArrayList<>();
                    for (ItemStack child : nested) if (child != null) kept.add(child);
                    meta.setItems(kept); item.setItemMeta(meta);
                }
            } catch (RuntimeException | LinkageError error) {
                output[i] = null;
                String message = "Could not filter " + item.getType() + ": " + error;
                if (warnings.add(message) && plugin != null) plugin.getLogger().warning(message);
            }
        }
        return output;
    }

    /** why: legacy static safety-check shim retained for external/legacy compatibility; delegates to active context filter when available. */
    public static boolean isSafe(ItemStack item) {
        KitsContext kits = activeContext();
        if (kits != null && kits.itemFilter() != null) {
            return kits.itemFilter().allowed(item);
        }
        return true; // why: survivor fallback remains fail-open when kits context/filter unavailable to preserve legacy behavior.
    }

    /** Whether one item passes the configured kit-room policy; exposed for staged cosmetic edits. */
    public boolean allowed(ItemStack item) {
        if (!settings.enabled() || item == null || item.getType().isAir()) return true;
        if (settings.kitRoomOnly() && !whitelist.contains(item.getType().name())) return false;
        if (item.getAmount() < 1 || item.getAmount() > item.getMaxStackSize()) return false;
        ItemFilterRules.Rule rule = settings.forMaterial(item.getType());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!rule.unbreakable() && meta.isUnbreakable()) return false;
            if (!rule.attributes() && meta.hasAttributeModifiers()) return false;
            if (!rule.flags() && !meta.getItemFlags().isEmpty()) return false;
        }
        if (!allowedEnchants(item, item.getEnchantments(), rule, false)) return false;
        if (meta instanceof EnchantmentStorageMeta book && !allowedEnchants(item, book.getStoredEnchants(), rule, true)) return false;
        if (meta instanceof PotionMeta potion && rule.checksPotions()) {
            for (PotionEffect effect : PotionEffectsCompat.baseEffects(potion)) if (!allowedEffect(effect, rule)) return false;
            for (PotionEffect effect : potion.getCustomEffects()) if (!allowedEffect(effect, rule)) return false;
        }
        return true;
    }

    /**
     * Configured level cap for one enchantment on an item. {@code 0} means no configured cap, so
     * callers fall back to the enchantment's vanilla maximum; the cap applies to stored enchants
     * on books as well as applied enchants on other items.
     */
    public int maxLevel(ItemStack item, Enchantment enchantment) {
        if (enchantment == null || item == null || item.getType().isAir()) return 0;
        Integer max = settings.forMaterial(item.getType()).enchantments().get(enchantment.getKey().toString());
        return max == null ? 0 : max;
    }

    /** Read-only: whether this filter enforces any configured rules at all. */
    public boolean enabled() {
        return settings.enabled();
    }

    /**
     * The level ceiling the editor may offer. A positive configured cap wins; an absent or
     * zero cap falls back to the enchantment's vanilla maximum, exactly as {@link #maxLevel}
     * and {@link #allowedEnchants} treat the tri-state cap. This is the single source for
     * the browser ceiling so Apply can never disagree with what the browser offers.
     */
    public int effectiveMaxLevel(ItemStack item, Enchantment enchantment) {
        if (enchantment == null || item == null || item.getType().isAir()) return 1;
        int configured = maxLevel(item, enchantment);
        if (configured > 0) return configured;
        try {
            return Math.max(1, enchantment.getMaxLevel());
        } catch (RuntimeException | LinkageError ex) {
            return 1;
        }
    }

    /**
     * Read-only view of the effective conflicting-enchant rule for this item. When the
     * filter is disabled there is no enforcement, so callers must not present a red badge.
     */
    public boolean allowsIncompatible(ItemStack item) {
        if (!settings.enabled() || item == null || item.getType().isAir()) return true;
        return settings.forMaterial(item.getType()).incompatible();
    }

    private boolean allowedEnchants(ItemStack item, Map<Enchantment, Integer> enchants, ItemFilterRules.Rule rule, boolean stored) {
        for (var entry : enchants.entrySet()) {
            Enchantment enchant = entry.getKey(); int level = entry.getValue();
            Integer max = rule.enchantments().get(enchant.getKey().toString());
            if (level < 1) return false;
            if (max != null) {
                if (max > 0 && level > max) return false;
            } else if (!rule.overLevelled() && level > enchant.getMaxLevel()) return false;
            if (!rule.incompatible()) {
                if (!stored && !enchant.canEnchantItem(item)) return false;
                for (Enchantment other : enchants.keySet())
                    if (other != enchant && (enchant.conflictsWith(other) || other.conflictsWith(enchant))) return false;
            }
        }
        return true;
    }

    private boolean allowedEffect(PotionEffect effect, ItemFilterRules.Rule rule) {
        long level = (long) effect.getAmplifier() + 1;
        int duration = effect.getDuration();
        if (level < 1 || duration < -1 || exceeds(level, duration, rule.potionLevel(), rule.potionDuration())) return false;
        ItemFilterRules.EffectLimit limit = rule.effects().get(effect.getType().getKey().toString());
        return limit == null || !exceeds(level, duration, limit.level(), limit.durationSeconds());
    }

    private boolean exceeds(long level, int ticks, int maxLevel, int maxSeconds) {
        return (maxLevel > 0 && level > maxLevel) || (maxSeconds > 0 && (ticks < 0 || ticks > (long) maxSeconds * 20));
    }

    public void addToWhitelist(Collection<ItemStack[]> pages) {
        for (ItemStack[] page : pages) if (page != null) for (ItemStack item : page) addWhitelistedItem(item, 0);
    }

    private void addWhitelistedItem(ItemStack item, int depth) {
        if (item == null || item.getType().isAir() || depth > MAX_CONTAINER_DEPTH) return;
        whitelist.add(item.getType().name());
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta block && block.getBlockState() instanceof Container container)
            for (ItemStack nested : container.getInventory().getContents()) addWhitelistedItem(nested, depth + 1);
        if (meta instanceof BundleMeta bundle) for (ItemStack nested : bundle.getItems()) addWhitelistedItem(nested, depth + 1);
    }

    public void clearWhitelist() { whitelist.clear(); }

    /** why: static shutdown retained for module reload/disable lifecycle cleanup across singleton-style static state. */
    public static void shutdown() {
        if (whitelist != null) whitelist.clear();
    }

    /** why: legacy context lookup helper retained to support static safety-check compatibility path. */
    private static KitsContext activeContext() {
        try {
            PvPCorePlugin plugin = JavaPlugin.getPlugin(PvPCorePlugin.class);
            return plugin == null ? null : plugin.kitsContext();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
