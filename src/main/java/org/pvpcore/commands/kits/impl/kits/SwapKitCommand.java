package org.pvpcore.commands.kits.impl.kits;

import org.pvpcore.features.kits.KitManager;
import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.commands.kits.impl.core.SlotArgumentParser;
import org.pvpcore.util.kits.KitSlots;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.SoundManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SwapKitCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayerAtLocation(sender, LocationFeature.KITS);
        if (player == null) {
            return true;
        }

        if (args.length != 2) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "command.swapkit-usage");
            SoundManager.playFailure(player);
            return true;
        }

        Integer slot1 = SlotArgumentParser.parseSlotInRange(args[0], 1, (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().maxKits() : 9));
        Integer slot2 = SlotArgumentParser.parseSlotInRange(args[1], 1, (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().maxKits() : 9));

        if (slot1 == null || slot2 == null) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "command.swapkit-usage");
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.invalid-numbers");
            SoundManager.playFailure(player);
            return true;
        }

        KitManager kitManager = org.pvpcore.commands.kits.KitsCommands.km();
        UUID uuid = player.getUniqueId();

        if (!kitManager.hasKit(uuid, slot1)) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.kit-slot-not-found", "slot", String.valueOf(slot1));
            SoundManager.playFailure(player);
            return true;
        }

        if (!kitManager.hasKit(uuid, slot2)) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.kit-slot-not-found", "slot", String.valueOf(slot2));
            SoundManager.playFailure(player);
            return true;
        }

        ItemStack[] tempkit = kitManager.getPlayerKit(uuid, slot1).clone();
        kitManager.savekit(uuid, slot1, kitManager.getPlayerKit(uuid, slot2), true);
        kitManager.savekit(uuid, slot2, tempkit.clone(), true);

        org.pvpcore.commands.kits.KitsCommands.lang().send(player, "success.kits-swapped", "slot1", String.valueOf(slot1), "slot2", String.valueOf(slot2));
        SoundManager.playSuccess(player);
        return true;
    }
}
