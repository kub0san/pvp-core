package org.pvpcore.commands.kits.impl.kits;

import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class EnderchestCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayerAtLocation(sender, LocationFeature.ENDERCHESTS);
        if (player == null) {
            return true;
        }

        viewOnlyEC(player);
        return true;
    }

    public void viewOnlyEC(Player player) {
        org.pvpcore.gui.kits.page.EnderchestViewPage.open(player);
    }
}
