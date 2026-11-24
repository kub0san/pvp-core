package org.pvpcore.commands.kits.impl.kits;

import org.pvpcore.features.kits.KitManager;
import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.commands.kits.impl.core.SlotArgumentParser;
import org.pvpcore.util.kits.KitSlots;
import org.pvpcore.util.kits.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pvpcore.util.kits.SoundManager;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DeleteKitCommand implements CommandExecutor {
    private final boolean enderchest;
    public DeleteKitCommand() { this(false); }
    public DeleteKitCommand(boolean enderchest) { this.enderchest = enderchest; }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayerAtLocation(sender, enderchest ? LocationFeature.ENDERCHESTS : LocationFeature.KITS);
        if (player == null) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (args.length != 1) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, enderchest ? "command.deleteec-usage" : "command.deletekit-usage");
            SoundManager.playFailure(player);
            return true;
        }

        Integer slot = SlotArgumentParser.parseSlotInRange(args[0], 1, (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().maxKits() : 9));
        KitManager kitManager = org.pvpcore.commands.kits.KitsCommands.km();
        if (slot == null) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, enderchest ? "command.deleteec-usage" : "command.deletekit-usage");
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.invalid-number");
            SoundManager.playFailure(player);
            return true;
        }

        if (!(enderchest ? kitManager.hasEC(uuid, slot) : kitManager.hasKit(uuid, slot))) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.kit-slot-not-found", "slot", String.valueOf(slot));
            SoundManager.playFailure(player);
            return true;
        }

        if (enderchest ? kitManager.deleteEnderchest(uuid, slot) : kitManager.deleteKit(uuid, slot)) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, enderchest ? "success.ec-deleted" : "success.kit-deleted", "slot", String.valueOf(slot));
            SoundManager.playSuccess(player);
        } else {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.kit-deletion-failed");
            SoundManager.playFailure(player);
        }

        return true;
    }
}
