package org.pvpcore.commands.kits.impl.share;

import org.pvpcore.features.kits.KitShareManager;
import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractShareResponseCommand implements CommandExecutor, TabCompleter {

    private final boolean accept;

    protected AbstractShareResponseCommand(boolean accept) {
        this.accept = accept;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = accept ? CommandGuards.requirePlayerAtLocation(sender, LocationFeature.SHARING) : CommandGuards.requirePlayer(sender);
        if (player == null) {
            return true;
        }

        String id = args.length > 0 ? args[0] : null;
        if (accept) {
            (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).acceptRequest(player, id);
        } else {
            (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).declineRequest(player, id);
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return List.of();
        }

        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        return (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).getPendingRequestIds(player).stream()
                .filter(id -> id.toLowerCase().startsWith(prefix))
                .toList();
    }
}
