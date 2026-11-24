package org.pvpcore.commands.kits.impl.core;

import org.pvpcore.commands.kits.KitsCommands;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.util.kits.LocationAccess;
import org.pvpcore.util.kits.LocationFeature;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class CommandGuards {

    private CommandGuards() {
    }

    private static KitsContext kits() {
        return KitsCommands.activeKits();
    }

    public static @Nullable Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        KitsContext kits = kits();
        if (kits != null && kits.lang() != null) kits.lang().sendNoPrefix(sender, "error.players-only");
        else sender.sendMessage("Only players can use this command.");
        return null;
    }

    public static @Nullable Player requirePlayer(CommandSender sender, String onlyPlayersMessage) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(onlyPlayersMessage);
        return null;
    }

    public static @Nullable Player requirePlayerAtLocation(CommandSender sender, LocationFeature feature) {
        Player player = requirePlayer(sender);
        if (player == null) return null;
        KitsContext kits = kits();
        LocationAccess access = kits != null ? kits.locationAccess() : null;
        if (access != null && !access.require(player, feature)) return null;
        if (!ActionGuards.dataReady(player)) return null;
        return player;
    }
}
