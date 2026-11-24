package org.pvpcore.commands.kits.impl.admin;

import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.pvpcore.util.kits.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.pvpcore.util.kits.SoundManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class KitRoomCommand implements CommandExecutor, TabCompleter {
    private static final String LOAD = "load";
    private static final String SAVE = "save";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        if (args.length != 1) {
            sendUsageError(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase(LOAD)) {
            (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitRoomDataManager() : null).loadFromDB();
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "success.kitroom-loaded");
            if (sender instanceof Player p) SoundManager.playSuccess(p);
            return true;
        }

        if (args[0].equalsIgnoreCase(SAVE)) {
            (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitRoomDataManager() : null).saveToDBAsync();
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "success.kitroom-saved");
            if (sender instanceof Player p) SoundManager.playSuccess(p);
            return true;
        }

        sendUsageError(sender);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add(SAVE);
            list.add(LOAD);
            return list;
        }
        return null;
    }

    private void sendUsageError(CommandSender sender) {
        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "error.incorrect-usage");
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "command.kitroom-usage-hint");
        if (sender instanceof Player p) {
            SoundManager.playFailure(p);
        }
    }
}