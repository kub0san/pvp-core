package org.pvpcore.features.kits;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.pvpcore.features.kits.regear.RegearEngine;
import org.pvpcore.util.kits.IDUtil;

/**
 * Kit item helpers extracted from KitManager: regear and load-time effects.
 * Keeps KitManager focused on cache/storage/selection flow.
 */
final class KitItemSupport {

    private KitItemSupport() {
    }

    static void applyLoadEffects(Player player, boolean isEnderChest, FileConfiguration config) {
        if (player.isDead() || config == null) {
            return;
        }
        String prefix = isEnderChest ? "enderchests.load." : "kits.load.";
        if (config.getBoolean(prefix + "heal", false)) {
            var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                player.setHealth(maxHealth.getValue());
            }
        }
        if (config.getBoolean(prefix + "feed", false)) {
            player.setFoodLevel(20);
        }
        if (config.getBoolean(prefix + "saturate", false)) {
            player.setSaturation(20);
        }
        if (config.getBoolean(prefix + "clear-effects", false)) {
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        }
        // Auto-apply potion effects carried by potions in the kit ("auto pot").
        if (!isEnderChest && config.getBoolean("kits.load.apply-potion-effects", false)) {
            if (player.isDead()) {
                return;
            }
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || !item.getType().name().contains("POTION")) {
                    continue;
                }
                if (!(item.getItemMeta() instanceof PotionMeta meta)) {
                    continue;
                }
                for (PotionEffect effect : meta.getCustomEffects()) {
                    player.addPotionEffect(effect);
                }
                if (meta.getBasePotionType() != null) {
                    for (PotionEffect effect : meta.getBasePotionType().getPotionEffects()) {
                        player.addPotionEffect(effect);
                    }
                }
            }
        }
    }

    private static void fail(Player player, org.pvpcore.util.kits.Lang lang, String reason) {
        org.bukkit.Bukkit.getLogger().warning("[REGEAR-DEBUG] regear aborted for " + player.getName() + ": " + reason);
        if (lang != null) lang.send(player, "error.regear-failed-reason", "reason", reason);
    }

    static boolean regearKit(Player player, int slot, KitCache cache, PlayerKitSession sessions, KitLoader loader,
                             ItemFilter itemFilter, FileConfiguration config) {
        return regear(player, IDUtil.getPlayerKitId(player.getUniqueId(), slot), cache, loader, itemFilter, config);
    }

    static boolean regearLastKit(Player player, KitCache cache, PlayerKitSession sessions, KitLoader loader,
                                 ItemFilter itemFilter, FileConfiguration config) {
        KitManager.KitReference ref = sessions.getLastKitReference(player.getUniqueId());
        return ref != null && regear(player, ref.key(player.getUniqueId()), cache, loader, itemFilter, config);
    }

    static boolean regear(Player player, String key, KitCache cache, KitLoader loader,
                          ItemFilter itemFilter, FileConfiguration config) {
        var lang = org.pvpcore.util.kits.Lang.get();
        if (!cache.contains(key)) {
            fail(player, lang, "kit not in cache: " + key);
            return false;
        }
        if (!loader.filterReady(player)) {
            fail(player, lang, "item filter not ready");
            return false;
        }
        ItemStack[] kit = itemFilter.filterKit(cache.getRaw(key), player);
        if (kit == null) {
            fail(player, lang, "kit rejected by item filter");
            return false;
        }
        RegearEngine.Result result = RegearEngine.apply(player, kit, config);
        if (!result.applied() && !result.blocked()) {
            fail(player, lang, "engine skipped (dead=" + player.isDead()
                    + ", inventory=" + player.getInventory().getContents().length + ", kit=" + kit.length + ")");
        }
        return result.applied();
    }
}
