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
package org.pvpcore.commands.kits.impl.admin;

import org.pvpcore.features.kits.ItemFilter;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;

public class SavePublicKitCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length < 1) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.missing-kit-id");
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "command.savepublickit-usage", "command", label);
            return true;
        }

        String kitId = args[0];

        if (org.pvpcore.commands.kits.KitsCommands.km().getPublicKitList().stream().noneMatch(kit -> kit.id.equals(kitId))) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.public-kit-not-found", "kitid", kitId);
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.add-public-kit-config");
            return true;
        }

        Inventory inv = player.getInventory();

        ItemStack[] data = new ItemStack[41];
//        copy inventory into data
        for (int i = 0; i < 41; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null) {
                data[i] = item.clone();
            }
        }


        data = (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().itemFilter() : null).filterItemStack(data);

        KitManager kitManager = org.pvpcore.commands.kits.KitsCommands.km();
        //save kit
        boolean success = kitManager.savePublicKit(kitId, data);
        if (success) {
            kitManager.savePublicKitToDB(kitId);
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "success.public-kit-saved-admin", "kitid", kitId);
            SoundManager.playSuccess(player);
        } else {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.public-kit-save-failed", "kitid", kitId);
            SoundManager.playFailure(player);
        }

        return true;


    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> ids = org.pvpcore.commands.kits.KitsCommands.km().getPublicKitList().stream().map(kit -> kit.id).toList();
            return ids.isEmpty() ? null : ids;
        }

        return null;
    }
}
