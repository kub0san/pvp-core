package org.pvpcore.commands.kits.impl.kits;

import org.pvpcore.features.kits.KitManager;
import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.gui.kits.GUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PublicKitCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;

    public PublicKitCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayerAtLocation(sender, LocationFeature.PUBLIC_KITS);
        if (player == null) {
            return true;
        }

        if (args.length < 1) {
            GUI kitMenu = GUI.getOrCreate(plugin, org.pvpcore.commands.kits.KitsCommands.activeKits());
            kitMenu.OpenPublicKitMenu(player);
            return true;
        }

        String kitName = args[0];
        org.pvpcore.commands.kits.KitsCommands.km().loadPublicKit(player, kitName);

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            org.pvpcore.commands.kits.KitsCommands.km().getPublicKitList().forEach((kit) -> list.add(kit.id));
            return list;
        }

        return null;
    }
}
