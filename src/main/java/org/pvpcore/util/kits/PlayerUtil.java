/*
 * Copyright 2022-2026 Noah Ross
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
package org.pvpcore.util.kits;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.pvpcore.PvPCorePlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerUtil {

    public static void repairItem(ItemStack i) {
        if (i != null) {
            ItemMeta meta = i.getItemMeta();
            if (meta instanceof Damageable damageable && damageable.hasDamage()) {
                damageable.setDamage(0);
                i.setItemMeta(damageable);
            }
        }

    }

    public static void repairAll(Player p) {
        for (ItemStack i : p.getInventory().getContents()) {
            repairItem(i);
        }
        var kits = kitsContext();
        if (kits != null && kits.lang() != null) kits.lang().send(p, "success.all-repaired");
    }

    public static void healPlayer(Player p) {
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);

        // Remove potion effects if configured to do so
        var kits = kitsContext();
        if (kits != null && kits.config() != null && kits.config().getBoolean("heal.clear-effects", false)) {
            p.getActivePotionEffects().forEach(potionEffect -> p.removePotionEffect(potionEffect.getType()));
        }
        if (kits != null && kits.lang() != null) kits.lang().send(p, "success.healed");
    }

    public static void healPlayerSilent(Player p) {
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
    }

    private static org.pvpcore.features.kits.KitsContext kitsContext() {
        try {
            PvPCorePlugin plugin = JavaPlugin.getPlugin(PvPCorePlugin.class);
            return plugin == null ? null : plugin.kitsContext();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static @NotNull String getPlayerName(@NotNull UUID uuid) {
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        return fallbackPlayerName(offlinePlayer.getName(), uuid);
    }

    static @NotNull String fallbackPlayerName(String playerName, @NotNull UUID uuid) {
        return playerName != null ? playerName : uuid.toString();
    }

    public static @NotNull List<String> completeOnlinePlayerNames(@NotNull Player self, @NotNull String prefix) {
        String lowered = prefix.toLowerCase();
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(self.getUniqueId())) {
                continue;
            }
            if (online.getName().toLowerCase().startsWith(lowered)) {
                names.add(online.getName());
            }
        }
        return names;
    }

}
