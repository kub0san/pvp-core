package org.pvpcore.commands.kits.impl.kits;

import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.gui.kits.GUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class MainMenuCommand implements CommandExecutor {

    private final Plugin plugin;

    public MainMenuCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(commandSender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayerAtLocation(commandSender, LocationFeature.MENU);
        if (player == null) {
            return true;
        }

        // "settings" opens the staff/plugin settings menu instead of the kit menu.
        if (strings.length >= 1 && strings[0].equalsIgnoreCase("settings")) {
            if (!player.hasPermission("perplayerkit.editkitroom") && !player.hasPermission("perplayerkit.admin")) {
                org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.no-permission");
                return true;
            }
            GUI.getOrCreate(plugin, org.pvpcore.commands.kits.KitsCommands.activeKits()).OpenMainManagerMenu(player);
            return true;
        }

        GUI main = GUI.getOrCreate(plugin, org.pvpcore.commands.kits.KitsCommands.activeKits());
        main.OpenMainMenu(player);
        return true;
    }
}
