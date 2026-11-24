package org.pvpcore.commands.kits.impl.share;

import org.pvpcore.features.kits.KitShareManager;
import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.CooldownManager;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.PlayerUtil;
import org.pvpcore.util.kits.SoundManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TransferKitsCommand implements CommandExecutor, TabCompleter {

    private static final int COOLDOWN_SECONDS = 5;
    private final CooldownManager cooldownManager = new CooldownManager(COOLDOWN_SECONDS);

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        Player player = CommandGuards.requirePlayerAtLocation(sender, LocationFeature.SHARING);
        if (player == null) {
            return true;
        }

        if (args.length < 1) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.missing-transfer-target");
            SoundManager.playFailure(player);
            return true;
        }

        if (cooldownManager.isOnCooldown(player)) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.command-cooldown");
            SoundManager.playFailure(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.share-player-not-found");
            SoundManager.playFailure(player);
            return true;
        }

        (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().kitShareManager() : null).sendTransferRequest(player, target);
        cooldownManager.setCooldown(player);
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
        return PlayerUtil.completeOnlinePlayerNames(player, args[0]);
    }
}
