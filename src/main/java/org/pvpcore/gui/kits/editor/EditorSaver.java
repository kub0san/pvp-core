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
package org.pvpcore.gui.kits.editor;

import org.pvpcore.gui.kits.*;

import org.pvpcore.features.kits.ItemFilter;
import org.pvpcore.features.kits.KitContents;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.util.kits.Lang;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.UUID;

/**
 * Saves editors on close and on canvas redraw navigation, which does not fire a close event.
 *
 * <p>Only valid for the compact editors: the top inventory must have exactly
 * the size declared by {@link GUI.EditorType#topSize()} (45 slots for a kit,
 * 36 for an ender chest), and the copy range is derived from the same type. A
 * screen with a different shape -- the kit-room item editor, for instance -- must
 * not be saved through here; it has its own save handler.</p>
 */
public final class EditorSaver {
    private EditorSaver() {}

    public static void save(KitsContext kitsCtx, Player player, GUI.EditorContext context, Inventory inventory) {
        boolean cleared = GUI.takeClearFlag(player);
        boolean inspection = context.type() == GUI.EditorType.INSPECT_KIT || context.type() == GUI.EditorType.INSPECT_ENDERCHEST;
        if (inspection) {
            boolean deleted = GUI.removeKitDeletionFlag(player);
            // Staff inspection is read-only; closing it is not an attempted admin action.
            if (deleted || context.target() == null || !player.hasPermission("perplayerkit.admin")) return;
        }
        String permission = switch (context.type()) {
            case KIT -> "perplayerkit.kit";
            case ENDERCHEST -> "perplayerkit.enderchest";
            default -> "perplayerkit.admin";
        };
        if (!ActionGuards.allowed(player, permission)) return;
        ItemStack[] view = inventory.getContents();
        if (view == null || view.length != context.type().topSize()) return;

        switch (context.type()) {
            case KIT, ENDERCHEST -> savePersonal(kitsCtx, player, context, view, cleared);
            case PUBLIC_KIT -> savePublicKit(kitsCtx, player, context.id(), context.type(), view);
            case INSPECT_KIT, INSPECT_ENDERCHEST -> saveInspection(kitsCtx, player, context, view);
        }
    }

    static ItemStack[] editableContents(ItemStack[] view, GUI.EditorType type) {
        int start = type.contentStart();
        int end = type.contentEnd();
        return KitContents.copyRange(view, start, end - start);
    }

    private static void savePersonal(KitsContext kitsCtx, Player player, GUI.EditorContext context,
                                     ItemStack[] view, boolean cleared) {
        boolean enderchest = context.type().enderchest();
        int slot = context.slot();
        KitManager kitManager = kitsCtx.kitManager();
        UUID uuid = player.getUniqueId();
        ItemStack[] items = editableContents(view, context.type());
        ItemStack[] stored = enderchest ? kitManager.getPlayerEC(uuid, slot) : kitManager.getPlayerKit(uuid, slot);
        if (!cleared && Arrays.equals(items, kitsCtx.itemFilter().filterItemStack(stored))) return;
        if (KitContents.isEmpty(items)) {
            String permission = enderchest ? "perplayerkit.deleteenderchest" : "perplayerkit.deletekit";
            if (stored != null && ActionGuards.allowed(player, permission)) {
                if (enderchest) kitManager.deleteEnderchest(uuid, slot);
                else kitManager.deleteKit(uuid, slot);
                kitsCtx.lang().send(player, enderchest ? "success.ec-deleted" : "success.kit-deleted", "slot", String.valueOf(slot));
            }
        } else if (enderchest) kitManager.saveEC(uuid, slot, items);
        else kitManager.savekit(uuid, slot, items);
    }

    private static void savePublicKit(KitsContext kitsCtx, Player player, String id,
                                      GUI.EditorType type, ItemStack[] view) {
        if (id == null || id.isEmpty()) return;
        KitManager kitManager = kitsCtx.kitManager();
        ItemStack[] items = editableContents(view, type);
        if (KitContents.isEmpty(items)) kitManager.deletePublicKit(id);
        else if (!Arrays.equals(items, kitManager.getPublicKit(id))) kitManager.savePublicKit(player, id, items);
    }

    private static void saveInspection(KitsContext kitsCtx, Player player, GUI.EditorContext context, ItemStack[] view) {
        boolean enderchest = context.type().enderchest();
        ItemStack[] items = editableContents(view, context.type());
        KitManager kitManager = kitsCtx.kitManager();
        ItemStack[] stored = enderchest ? kitManager.getPlayerEC(context.target(), context.slot()) : kitManager.getPlayerKit(context.target(), context.slot());
        if (Arrays.equals(items, stored)) return;
        boolean saved = enderchest ? kitManager.saveECSilent(context.target(), context.slot(), items)
                : kitManager.savekit(context.target(), context.slot(), items, true);
        String success = enderchest ? "success.admin-ec-updated" : "success.admin-kit-updated";
        String failure = enderchest ? "error.failed-to-update-ec" : "error.failed-to-update-kit";
        kitsCtx.lang().send(player, saved ? success : failure, "slot", String.valueOf(context.slot()), "player", context.playerName());
    }
}
