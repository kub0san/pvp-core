package org.pvpcore.commands.kits.impl.inspect;

import org.pvpcore.util.kits.BroadcastManager;
import org.pvpcore.util.kits.KitSlots;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.SoundManager;
import org.bukkit.Bukkit;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.pvpcore.commands.kits.impl.inspect.InspectCommandUtil.errorPrefix;
import static org.pvpcore.commands.kits.impl.inspect.InspectCommandUtil.resolvePlayerIdentifierAsync;
import static org.pvpcore.commands.kits.impl.inspect.InspectCommandUtil.showUsage;
import static org.pvpcore.util.kits.PlayerUtil.getPlayerName;

public abstract class AbstractInspectCommand implements CommandExecutor, TabCompleter {
    protected final Plugin plugin;

    protected AbstractInspectCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    protected abstract String usageCommand();

    protected abstract boolean hasData(UUID targetUuid, int slot);

    protected abstract void openInspectGui(Player inspector, UUID targetUuid, int slot);

    protected abstract void loadSlotFromDB(UUID targetUuid, int slot);

    protected abstract String missingDataKey();

    protected abstract String loadErrorLogMessage();

    protected abstract String loadErrorUserKey();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        if (!(sender instanceof Player player)) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "error.players-only-inspect");
            return true;
        }

        if (args.length < 2) {
            showUsage(player, usageCommand());
            return true;
        }

        int slot = parseSlot(args[1], player);
        if (slot == -1) {
            return true;
        }

        UUID senderUuid = player.getUniqueId();
        CompletableFuture<Void> future = resolvePlayerIdentifierAsync(args[0])
                .thenCompose(targetUuid -> {
                    if (targetUuid == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player currentSender = Bukkit.getPlayer(senderUuid);
                            if (currentSender == null) {
                                return;
                            }
                            showPlayerNotFound(currentSender);
                        });
                        return CompletableFuture.completedFuture(null);
                    }

                    CompletableFuture<Boolean> targetOnlineFuture = new CompletableFuture<>();
                    Bukkit.getScheduler().runTask(plugin,
                            () -> targetOnlineFuture.complete(Bukkit.getPlayer(targetUuid) != null));

                    return targetOnlineFuture.thenCompose(targetOnline -> {
                        CompletableFuture<Void> loadFuture = targetOnline
                                ? CompletableFuture.completedFuture(null)
                                : CompletableFuture.runAsync(() -> loadSlotFromDB(targetUuid, slot));

                        return loadFuture.thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                            Player currentSender = Bukkit.getPlayer(senderUuid);
                            if (currentSender == null) {
                                return;
                            }

                            Player currentTarget = Bukkit.getPlayer(targetUuid);
                            showInspectResult(currentSender, currentTarget, targetUuid, slot);
                        }));
                    });
                });

        future.exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().severe(loadErrorLogMessage() + ": " + ex.getMessage());
                Player currentSender = Bukkit.getPlayer(senderUuid);
                if (currentSender == null) {
                    return;
                }

                (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().broadcastManager() : null).sendComponentMessage(currentSender,
                        errorPrefix().append(org.pvpcore.commands.kits.KitsCommands.lang().component(loadErrorUserKey())));
                SoundManager.playFailure(currentSender);
            });
            return null;
        });

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return List.of();
        }

        if (!(sender instanceof Player)) {
            return List.of();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> completions = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .toList());
            if (input.length() >= 4 && input.contains("-")) {
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getUniqueId)
                        .map(UUID::toString)
                        .filter(uuid -> uuid.startsWith(input))
                        .toList());
            }
            return completions;
        }

        if (args.length == 2) {
            return IntStream.rangeClosed(1, (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().maxKits() : 9))
                    .mapToObj(String::valueOf)
                    .filter(slot -> slot.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private int parseSlot(String slotArg, Player player) {
        try {
            int slot = Integer.parseInt(slotArg);
            if (slot < 1 || slot > 99) {
                throw new NumberFormatException();
            }
            return slot;
        } catch (NumberFormatException e) {
            (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().broadcastManager() : null).sendComponentMessage(player,
                    errorPrefix().append(org.pvpcore.commands.kits.KitsCommands.lang().component("error.invalid-slot-range",
                            "min", String.valueOf(1), "max", String.valueOf(99))));
            SoundManager.playFailure(player);
            return -1;
        }
    }

    private void showInspectResult(Player inspector, @Nullable Player targetPlayer, UUID targetUuid, int slot) {
        if (hasData(targetUuid, slot)) {
            openInspectGui(inspector, targetUuid, slot);
            return;
        }

        String targetName = targetPlayer != null ? targetPlayer.getName() : getPlayerName(targetUuid);
        if (targetName == null) {
            targetName = targetUuid.toString();
        }
        (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().broadcastManager() : null).sendComponentMessage(inspector,
                errorPrefix().append(org.pvpcore.commands.kits.KitsCommands.lang().component(missingDataKey(),
                        "player", targetName, "slot", String.valueOf(slot))));
        SoundManager.playFailure(inspector);
    }

    private void showPlayerNotFound(Player player) {
        (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().broadcastManager() : null).sendComponentMessage(player,
                errorPrefix().append(org.pvpcore.commands.kits.KitsCommands.lang().component("error.player-not-found")));
        SoundManager.playFailure(player);
    }
}
