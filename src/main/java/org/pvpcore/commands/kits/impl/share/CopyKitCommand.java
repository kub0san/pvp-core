package org.pvpcore.commands.kits.impl.share;

import org.pvpcore.features.kits.KitShareManager;
import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pvpcore.util.kits.SoundManager;
import org.jetbrains.annotations.NotNull;

public class CopyKitCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayerAtLocation(sender, LocationFeature.SHARING);
        if (player == null) {
            return true;
        }

        if (args.length > 0) {
            (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).copyKit(player, args[0]);
        } else {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.missing-kit-code");
            SoundManager.playFailure(player);
        }

        return true;
    }
}
