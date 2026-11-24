package org.pvpcore.commands.kits.impl.share;

import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.commands.kits.impl.core.SlotArgumentParser;
import org.pvpcore.util.CooldownManager;
import org.pvpcore.util.kits.KitSlots;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.SoundManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public abstract class AbstractShareSlotCommand implements CommandExecutor {

    @FunctionalInterface
    public interface ShareRequestAction {
        void share(Player sender, int slot, Player target);
    }

    private static final int COOLDOWN_SECONDS = 5;
    private final CooldownManager cooldownManager = new CooldownManager(COOLDOWN_SECONDS);
    private final String missingSlotMessageKey;
    private final BiConsumer<Player, Integer> codeShareAction;
    private final ShareRequestAction requestShareAction;

    protected AbstractShareSlotCommand(String missingSlotMessageKey, BiConsumer<Player, Integer> codeShareAction,
                                       ShareRequestAction requestShareAction) {
        this.missingSlotMessageKey = missingSlotMessageKey;
        this.codeShareAction = codeShareAction;
        this.requestShareAction = requestShareAction;
    }

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
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, missingSlotMessageKey);
            SoundManager.playFailure(player);
            return true;
        }

        if (cooldownManager.isOnCooldown(player)) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.command-cooldown");
            SoundManager.playFailure(player);
            return true;
        }

        Integer slot = SlotArgumentParser.parseSlotInRange(args[0], 1, (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().maxKits() : 9));
        if (slot == null) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.invalid-kit-slot");
            SoundManager.playFailure(player);
            return true;
        }

        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                org.pvpcore.commands.kits.KitsCommands.lang().send(player, "error.share-player-not-found");
                SoundManager.playFailure(player);
                return true;
            }
            requestShareAction.share(player, slot, target);
        } else {
            codeShareAction.accept(player, slot);
        }
        cooldownManager.setCooldown(player);
        return true;
    }
}
