package org.pvpcore.commands.kits.impl.features;

import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.BroadcastManager;
import org.pvpcore.util.kits.PlayerUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pvpcore.util.kits.SoundManager;
import org.jetbrains.annotations.NotNull;

public class HealCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayerAtLocation(sender, LocationFeature.HEAL);
        if (player == null) {
            return true;
        }

        (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().broadcastManager() : null).broadcastPlayerHealed(player);
        PlayerUtil.healPlayer(player);
        SoundManager.playSuccess(player);
        return true;
    }
}
