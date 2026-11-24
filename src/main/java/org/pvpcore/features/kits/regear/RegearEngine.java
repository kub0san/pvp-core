package org.pvpcore.features.kits.regear;

import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.pvpcore.features.kits.KitContents;
import org.pvpcore.util.kits.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Internal engine that computes a regear before touching the player's inventory.
 *
 * <p>Exact mode treats the saved kit as the layout authority for every included slot, including
 * blacklisted kit items: the player's own stack is claimed and MOVED into that kit slot instead of
 * being deleted. Anything that cannot be kept is routed to the configured overflow behaviour.
 */
@SuppressWarnings("deprecation") // Lang.get() is the established legacy accessor in this module.
public final class RegearEngine {

    private static final int MAX_CONTAINER_DEPTH = 16;

    private RegearEngine() {
    }

    public record Result(boolean applied, boolean blocked, int overflowCount) {
        static Result appliedResult(int overflow) { return new Result(true, false, overflow); }
        static Result blockedResult() { return new Result(false, true, 0); }
        static Result skippedResult() { return new Result(false, false, 0); }
    }

    public static Result apply(Player player, ItemStack[] kit, FileConfiguration config) {
        if (player == null || player.isDead() || kit == null || config == null) return Result.skippedResult();
        RegearRules rules = RegearRules.parse(config);
        ItemStack[] current = KitContents.copy(player.getInventory().getContents());
        // Paper reports 41 slots on some versions and 43 on others (body/saddle slots are
        // appended in 1.21.11+). Accept any array that covers the kit layout; never require == 41.
        if (current == null || current.length < KitContents.INVENTORY_SIZE) return Result.skippedResult();

        int[] slots = targetSlots(rules);
        if (!rules.restoreExact()) return nonExact(player, current, kit, rules, slots);
        return exact(player, current, kit, rules, slots);
    }

    private static int[] targetSlots(RegearRules rules) {
        int count = 36 + (rules.restoreArmor() ? 4 : 0) + (rules.restoreOffhand() ? 1 : 0);
        int[] slots = new int[count];
        int n = 0;
        for (int i = 0; i < 36; i++) slots[n++] = i;
        if (rules.restoreArmor()) for (int i = 36; i <= 39; i++) slots[n++] = i;
        if (rules.restoreOffhand()) slots[n] = 40;
        return slots;
    }

    /** Legacy behaviour: fill empty/matching slots, never delete or reposition anything. */
    private static Result nonExact(Player player, ItemStack[] current, ItemStack[] kit,
                                   RegearRules rules, int[] slots) {
        ItemStack[] next = current.clone();
        boolean changed = false;
        for (int slot : slots) {
            ItemStack kitItem = slot < kit.length ? kit[slot] : null;
            if (kitItem == null || kitItem.getType().isAir() || rules.excluded(kitItem)) continue;
            ItemStack replacement = filterContents(kitItem.clone(), rules, 0);
            if (replacement == null) continue;
            ItemStack held = next[slot];
            if (held == null || held.getType().isAir() || held.getType() == replacement.getType()) {
                next[slot] = replacement;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setContents(next);
            player.updateInventory();
        }
        return Result.appliedResult(0);
    }

    private static Result exact(Player player, ItemStack[] current, ItemStack[] kit,
                                RegearRules rules, int[] slots) {
        boolean[] excludedSlot = new boolean[current.length];
        for (int slot : slots) {
            ItemStack kitItem = slot < kit.length ? kit[slot] : null;
            excludedSlot[slot] = kitItem != null && !kitItem.getType().isAir() && rules.excluded(kitItem);
        }

        // 1. Claim the player's own stacks for blacklisted kit positions before any slot is wiped.
        ItemStack[] claimed = new ItemStack[current.length];
        boolean[] used = new boolean[current.length];
        for (int slot : slots) {
            if (!excludedSlot[slot]) continue;
            Material material = kit[slot].getType();
            int max = Math.max(1, material.getMaxStackSize());

            // Prefer a stack in the kit position that is itself excluded from regear; then another
            // excluded stack; finally fall back to any same-material stack so nothing is destroyed.
            claimed[slot] = claimStack(current, used, slots, material, rules, true, slot);
            if (claimed[slot] == null) claimed[slot] = claimStack(current, used, slots, material, rules, true, -1);
            if (claimed[slot] == null) claimed[slot] = claimStack(current, used, slots, material, rules, false, slot);
            if (claimed[slot] == null) claimed[slot] = claimStack(current, used, slots, material, rules, false, -1);
            // Merge extra whole stacks into the claimed stack up to one full stack.
            while (claimed[slot] != null && claimed[slot].getAmount() < max) {
                int room = max - claimed[slot].getAmount();
                boolean merged = false;
                for (int candidate : slots) {
                    ItemStack stack = current[candidate];
                    if (used[candidate] || stack == null || stack.getType() != material) continue;
                    if (rules.excluded(stack) != rules.excluded(claimed[slot])) continue;
                    if (stack.getAmount() > room) continue;
                    claimed[slot].setAmount(claimed[slot].getAmount() + stack.getAmount());
                    used[candidate] = true;
                    merged = true;
                    break;
                }
                if (!merged) break;
            }
        }

        // 2. Rebuild the affected slots. Every displaced stack becomes overflow (unless claimed).
        ItemStack[] next = current.clone();
        List<ItemStack> overflow = new ArrayList<>();
        for (int slot : slots) {
            ItemStack held = current[slot];
            ItemStack replacement;
            if (excludedSlot[slot]) {
                replacement = claimed[slot]; // null when the player has no such stack — slot stays empty
            } else if (slot < kit.length && kit[slot] != null && !kit[slot].getType().isAir()) {
                replacement = filterContents(kit[slot].clone(), rules, 0);
            } else {
                replacement = null;
            }
            if (!used[slot] && replacement != null && held != null && replacement.equals(held)) continue;
            next[slot] = replacement;
            if (held != null && !held.getType().isAir() && !used[slot]) {
                overflow.add(held.clone());
            }
        }

        // 3. Drain overflow without silently deleting anything.
        switch (rules.overflowAction()) {
            case BLOCK -> {
                if (!overflow.isEmpty()) {
                    Lang lang = Lang.get();
                    if (lang != null) lang.send(player, "error.regear-overflow-blocked", "count", String.valueOf(overflow.size()));
                    return Result.blockedResult();
                }
            }
            case ENDERCHEST -> {
                for (ItemStack stack : overflow) {
                    for (ItemStack leftover : player.getEnderChest().addItem(stack).values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    }
                }
            }
            case DROP -> {
                for (ItemStack stack : overflow) {
                    player.getWorld().dropItemNaturally(player.getLocation(), stack);
                }
            }
        }
        if (!overflow.isEmpty()) {
            Lang lang = Lang.get();
            if (lang != null) {
                String key = rules.overflowAction() == RegearRules.OverflowAction.ENDERCHEST
                        ? "success.regear-overflow-stored" : "success.regear-overflow-dropped";
                lang.send(player, key, "count", String.valueOf(overflow.size()));
            }
        }

        player.getInventory().setContents(next);
        player.updateInventory();
        if (debugEnabled()) {
            org.bukkit.Bukkit.getLogger().warning("[REGEAR-DEBUG] exact apply: slots=" + slots.length
                    + " overflow=" + overflow.size() + " blockedSlots=" + count(excludedSlot));
        }
        return Result.appliedResult(overflow.size());
    }

    private static boolean debugEnabled() {
        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("pvp-core");
        return plugin != null && plugin.getConfig().getBoolean("debug", false);
    }

    private static int count(boolean[] flags) {
        int n = 0;
        for (boolean flag : flags) if (flag) n++;
        return n;
    }

    /** Takes one whole stack for a blacklisted kit slot; {@code onlySlot} >= 0 restricts the search. */
    private static ItemStack claimStack(ItemStack[] current, boolean[] used, int[] slots,
                                        Material material, RegearRules rules, boolean preferExcluded, int onlySlot) {
        for (int slot : slots) {
            if (onlySlot >= 0 && slot != onlySlot) continue;
            ItemStack stack = current[slot];
            if (used[slot] || stack == null || stack.getType() != material) continue;
            if (preferExcluded && !rules.excluded(stack)) continue;
            used[slot] = true;
            return stack.clone();
        }
        return null;
    }

    /**
     * Applies regear rules to kit items stored inside containers/bundles. Excluded nested items are
     * removed so nothing can be smuggled in through regear; the depth guard mirrors ItemFilter.
     */
    private static ItemStack filterContents(ItemStack item, RegearRules rules, int depth) {
        if (item == null || depth > MAX_CONTAINER_DEPTH) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockState && blockState.getBlockState() instanceof Container container) {
            ItemStack[] contents = container.getInventory().getContents();
            boolean changed = false;
            for (int i = 0; i < contents.length; i++) {
                ItemStack nested = contents[i];
                if (nested == null) continue;
                if (rules.excluded(nested)) {
                    contents[i] = null;
                    changed = true;
                } else {
                    ItemStack filtered = filterContents(nested, rules, depth + 1);
                    if (filtered == null || !filtered.equals(nested)) {
                        contents[i] = filtered;
                        changed = true;
                    }
                }
            }
            if (changed) {
                container.getInventory().setContents(contents);
                blockState.setBlockState(container);
                item.setItemMeta(blockState);
            }
        } else if (meta instanceof BundleMeta bundle && !bundle.getItems().isEmpty()) {
            List<ItemStack> kept = new ArrayList<>();
            boolean changed = false;
            for (ItemStack child : bundle.getItems()) {
                if (child == null || rules.excluded(child)) {
                    changed = true;
                    continue;
                }
                ItemStack filtered = filterContents(child, rules, depth + 1);
                if (filtered == null) {
                    changed = true;
                    continue;
                }
                if (!filtered.equals(child)) changed = true;
                kept.add(filtered);
            }
            if (changed) {
                bundle.setItems(kept);
                item.setItemMeta(bundle);
            }
        }
        return item;
    }
}
