package org.pvpcore.commands.kits.impl.completion;

import org.pvpcore.features.kits.KitShareManager;
import org.pvpcore.util.kits.PlayerUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class KitSlotTabCompleter implements TabCompleter {

    private final boolean completeTargetPlayer;

    public KitSlotTabCompleter() {
        this(false);
    }

    public KitSlotTabCompleter(boolean completeTargetPlayer) {
        this.completeTargetPlayer = completeTargetPlayer;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return List.of();
        }

        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (completeTargetPlayer && args.length >= 2) {
            if (args.length == 2) {
                return PlayerUtil.completeOnlinePlayerNames(player, args[1]);
            }
            return List.of();
        }
        return (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).getKitSlots(player);
    }
}
