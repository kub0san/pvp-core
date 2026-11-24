package org.pvpcore.commands.kits;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.CommandExecutor;
import dev.jorel.commandapi.executors.ConsoleCommandExecutor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.commands.kits.impl.admin.AboutCommandListener;
import org.pvpcore.commands.kits.impl.admin.KitDataCommand;
import org.pvpcore.commands.kits.impl.admin.KitRoomCommand;
import org.pvpcore.commands.kits.impl.admin.PerPlayerKitCommand;
import org.pvpcore.commands.kits.impl.admin.PurgeItemCommand;
import org.pvpcore.commands.kits.impl.admin.SavePublicKitCommand;
import org.pvpcore.commands.kits.impl.completion.ECSlotTabCompleter;
import org.pvpcore.commands.kits.impl.completion.KitSlotTabCompleter;
import org.pvpcore.commands.kits.impl.core.SlotArgumentParser;
import org.pvpcore.commands.kits.impl.features.HealCommand;
import org.pvpcore.commands.kits.impl.features.RegearCommand;
import org.pvpcore.commands.kits.impl.features.RepairCommand;
import org.pvpcore.commands.kits.impl.inspect.InspectEcCommand;
import org.pvpcore.commands.kits.impl.inspect.InspectKitCommand;
import org.pvpcore.commands.kits.impl.kits.DeleteKitCommand;
import org.pvpcore.commands.kits.impl.kits.EnderchestCommand;
import org.pvpcore.commands.kits.impl.kits.MainMenuCommand;
import org.pvpcore.commands.kits.impl.kits.PublicKitCommand;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.commands.kits.impl.kits.SwapKitCommand;
import org.pvpcore.commands.kits.impl.share.CopyKitCommand;
import org.pvpcore.commands.kits.impl.share.ShareAcceptCommand;
import org.pvpcore.commands.kits.impl.share.ShareDeclineCommand;
import org.pvpcore.commands.kits.impl.share.ShareECKitCommand;
import org.pvpcore.commands.kits.impl.share.ShareKitCommand;
import org.pvpcore.commands.kits.impl.share.TransferKitsCommand;
import org.pvpcore.commands.kits.impl.shortcuts.ShortECCommand;
import org.pvpcore.commands.kits.impl.shortcuts.ShortKitCommand;
import org.pvpcore.commands.kits.shortcuts.ShortcutCommandRegistrar;
import org.pvpcore.util.kits.KitSlots;
import org.pvpcore.util.kits.Lang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Global command registration wiring for the kits module.
 */
public final class KitsCommands {

    private static final String[] NO_ARGS = new String[0];
    private static volatile KitsContext activeKits;

    private KitsCommands() {
    }

    public static KitsContext activeKits() {
        return activeKits;
    }

    public static void setActiveKits(KitsContext kits) {
        activeKits = kits;
    }

    public static org.pvpcore.util.kits.Lang lang() {
        KitsContext kits = activeKits;
        return kits != null ? kits.lang() : null;
    }

    public static org.pvpcore.features.kits.KitManager km() {
        KitsContext kits = activeKits;
        return kits != null ? kits.kitManager() : null;
    }

    public static org.pvpcore.features.kits.KitManager kitManager() {
        return km();
    }

    public static int maxKits() {
        KitsContext kits = activeKits;
        return kits != null ? kits.maxKits() : 9;
    }

    public static void register(PvPCorePlugin plugin) {
        // Bukkit wiring is module-lifecycle driven via registerBukkitCommands().
    }

    public static boolean isKitsSub(String sub) {
        if (sub == null) return false;
        return switch (sub.toLowerCase(Locale.ROOT)) {
            case "kit", "ec", "enderchest", "publickit", "kitroom", "regear", "heal", "repair",
                 "sharekit", "shareec", "shareaccept", "sharedecline", "copykit", "swapkit",
                 "deletekit", "deleteec", "inspectkit", "inspectec", "transferkits",
                 "savepublickit", "purgeitem", "kitdata", "perplayerkit", "aboutperplayerkit" -> true;
            default -> false;
        };
    }

    public static List<CommandAPICommand> subcommands(PvPCorePlugin plugin) {
        return subcommands(plugin, plugin.kitsContext());
    }

    public static List<CommandAPICommand> subcommands(PvPCorePlugin plugin, KitsContext kits) {
        MainMenuCommand kitMenu = new MainMenuCommand(plugin);
        ShortKitCommand kitSlot = new ShortKitCommand();
        ShortECCommand ecSlot = new ShortECCommand();
        EnderchestCommand enderchestView = new EnderchestCommand();
        PublicKitCommand publicKit = new PublicKitCommand(plugin);
        KitRoomCommand kitRoom = new KitRoomCommand();
        HealCommand heal = new HealCommand();
        RepairCommand repair = new RepairCommand();
        ShareKitCommand shareKit = new ShareKitCommand();
        ShareECKitCommand shareEc = new ShareECKitCommand();
        ShareAcceptCommand shareAccept = new ShareAcceptCommand();
        ShareDeclineCommand shareDecline = new ShareDeclineCommand();
        CopyKitCommand copyKit = new CopyKitCommand();
        SwapKitCommand swapKit = new SwapKitCommand();
        DeleteKitCommand deleteKit = new DeleteKitCommand();
        DeleteKitCommand deleteEc = new DeleteKitCommand(true);
        InspectKitCommand inspectKit = new InspectKitCommand(plugin);
        InspectEcCommand inspectEc = new InspectEcCommand(plugin);
        TransferKitsCommand transferKits = new TransferKitsCommand();
        SavePublicKitCommand savePublicKit = new SavePublicKitCommand();
        PurgeItemCommand purgeItem = new PurgeItemCommand(plugin);
        KitDataCommand kitData = new KitDataCommand(plugin);
        PerPlayerKitCommand perPlayerKit = new PerPlayerKitCommand(plugin);
        AboutCommandListener about = new AboutCommandListener();

        List<CommandAPICommand> subs = new ArrayList<>();
        subs.add(kitTree(plugin, kits, kitMenu, kitSlot));
        subs.add(ecTree(plugin, kits, "ec", enderchestView, ecSlot));
        subs.add(ecTree(plugin, kits, "enderchest", enderchestView, ecSlot));
        subs.add(execTree(plugin, "publickit", publicKit, publicKit));
        subs.add(execTree(plugin, "kitroom", kitRoom, kitRoom));
        subs.add(regearTree(plugin, kits));
        subs.add(execTree(plugin, "heal", heal, null));
        subs.add(execTree(plugin, "repair", repair, null));
        subs.add(execTree(plugin, "sharekit", shareKit, new KitSlotTabCompleter(true)));
        subs.add(execTree(plugin, "shareec", shareEc, new ECSlotTabCompleter(true)));
        subs.add(execTree(plugin, "shareaccept", shareAccept, shareAccept));
        subs.add(execTree(plugin, "sharedecline", shareDecline, shareDecline));
        subs.add(execTree(plugin, "copykit", copyKit, null));
        subs.add(execTree(plugin, "swapkit", swapKit, new KitSlotTabCompleter(false)));
        subs.add(execTree(plugin, "deletekit", deleteKit, new KitSlotTabCompleter(false)));
        subs.add(execTree(plugin, "deleteec", deleteEc, new KitSlotTabCompleter(false)));
        subs.add(execTree(plugin, "inspectkit", inspectKit, inspectKit));
        subs.add(execTree(plugin, "inspectec", inspectEc, inspectEc));
        subs.add(execTree(plugin, "transferkits", transferKits, transferKits));
        subs.add(execTree(plugin, "savepublickit", savePublicKit, savePublicKit));
        subs.add(execTree(plugin, "purgeitem", purgeItem, purgeItem));
        subs.add(execTree(plugin, "kitdata", kitData, kitData));
        subs.add(execTree(plugin, "perplayerkit", perPlayerKit, perPlayerKit));
        subs.add(execTree(plugin, "aboutperplayerkit", about, null));
        return subs;
    }

    private static CommandAPICommand kitTree(PvPCorePlugin plugin, KitsContext kits, MainMenuCommand menu, ShortKitCommand slotCmd) {
        StringArgument slot = slotArg(kits);
        return new CommandAPICommand("kit")
                .withOptionalArguments(slot)
                .executes((CommandExecutor) (sender, args) -> handleKitLike(plugin, kits, sender, args, menu, slotCmd, false))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> handleKitLike(plugin, kits, sender, args, menu, slotCmd, false));
    }

    private static CommandAPICommand ecTree(PvPCorePlugin plugin, KitsContext kits, String name, EnderchestCommand view, ShortECCommand slotCmd) {
        StringArgument slot = slotArg(kits);
        return new CommandAPICommand(name)
                .withOptionalArguments(slot)
                .executes((CommandExecutor) (sender, args) -> handleKitLike(plugin, kits, sender, args, view, slotCmd, true))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> handleKitLike(plugin, kits, sender, args, view, slotCmd, true));
    }

    private static void handleKitLike(PvPCorePlugin plugin, KitsContext kits, CommandSender sender, CommandArguments args,
                                      org.bukkit.command.CommandExecutor noArgExecutor,
                                      Object slotCmd, boolean ec) {
        if (kits == null || !kits.isEnabled()) {
            if (kits != null && kits.lang() != null) kits.lang().sendNoPrefix(sender, "error.module-disabled");
            else sender.sendMessage("Kits are currently disabled.");
            return;
        }
        String rawSlot = (String) args.getOptional("slot").orElse(null);
        Command pvpcore = pvpcoreCommand(plugin);
        String label = ec ? "ec" : "kit";
        if (rawSlot != null && rawSlot.equalsIgnoreCase("settings")) {
            if (!(sender instanceof Player player)) {
                if (kits.lang() != null) kits.lang().sendNoPrefix(sender, "error.players-only");
                return;
            }
            if (!player.hasPermission("perplayerkit.editkitroom") && !player.hasPermission("perplayerkit.admin")) {
                if (kits.lang() != null) kits.lang().send(player, "error.no-permission");
                return;
            }
            GUI.getOrCreate(plugin, kits).OpenMainManagerMenu(player);
            return;
        }
        if (rawSlot == null || rawSlot.isBlank()) {
            noArgExecutor.onCommand(sender, pvpcore, label, NO_ARGS);
            return;
        }
        if (!(sender instanceof Player player)) {
            if (kits.lang() != null) kits.lang().sendNoPrefix(sender, "error.players-only");
            return;
        }
        int max = kits.maxKits();
        Integer slot = SlotArgumentParser.parseSlotInRange(rawSlot, 1, max);
        if (slot == null) {
            if (kits.lang() != null) {
                kits.lang().send(player, "error.invalid-slot-range",
                        "min", "1", "max", String.valueOf(max));
            }
            return;
        }
        if (ec) ((ShortECCommand) slotCmd).runForSlot(player, slot);
        else ((ShortKitCommand) slotCmd).runForSlot(player, slot);
    }

    private static CommandAPICommand regearTree(PvPCorePlugin plugin, KitsContext kits) {
        GreedyStringArgument tail = greedyTail();
        return new CommandAPICommand("regear")
                .withOptionalArguments(tail)
                .executes((CommandExecutor) (sender, args) -> {
                    RegearCommand regear = kits != null ? kits.regearCommand() : null;
                    if (regear == null) {
                        sender.sendMessage(plugin.messages().get("prefix").append(
                                net.kyori.adventure.text.Component.text(
                                        "Kits is disabled; /pvp-core regear is unavailable.",
                                        net.kyori.adventure.text.format.NamedTextColor.RED)));
                        return;
                    }
                    regear.onCommand(sender, pvpcoreCommand(plugin), "regear", tailArgs(args));
                })
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> {
                    RegearCommand regear = kits != null ? kits.regearCommand() : null;
                    if (regear == null) {
                        sender.sendMessage(plugin.messages().get("prefix").append(
                                net.kyori.adventure.text.Component.text(
                                        "Kits is disabled; /pvp-core regear is unavailable.",
                                        net.kyori.adventure.text.format.NamedTextColor.RED)));
                        return;
                    }
                    regear.onCommand(sender, pvpcoreCommand(plugin), "regear", tailArgs(args));
                });
    }

    private static CommandAPICommand execTree(PvPCorePlugin plugin, String sub,
                                              org.bukkit.command.CommandExecutor executor,
                                              TabCompleter completer) {
        GreedyStringArgument tail = greedyTail();
        tail.replaceSuggestions(ArgumentSuggestions.strings(info -> {
            if (completer == null) return new String[0];
            return suggestFromCompleter(plugin, completer, sub, info.sender(), info.currentArg());
        }));
        return new CommandAPICommand(sub)
                .withOptionalArguments(tail)
                .executes((CommandExecutor) (sender, args) ->
                        executor.onCommand(sender, pvpcoreCommand(plugin), sub, tailArgs(args)))
                .executesConsole((ConsoleCommandExecutor) (sender, args) ->
                        executor.onCommand(sender, pvpcoreCommand(plugin), sub, tailArgs(args)));
    }

    private static StringArgument slotArg(KitsContext kits) {
        StringArgument slot = new StringArgument("slot");
        slot.replaceSuggestions(ArgumentSuggestions.strings(info -> slotSuggestions(kits)));
        return slot;
    }

    private static String[] slotSuggestions(KitsContext kits) {
        int max = kits != null ? kits.maxKits() : 9;
        max = Math.min(max, 99);
        List<String> slots = new ArrayList<>(Math.min(max, 18));
        for (int i = 1; i <= max; i++) slots.add(String.valueOf(i));
        return slots.toArray(new String[0]);
    }

    private static GreedyStringArgument greedyTail() {
        GreedyStringArgument tail = new GreedyStringArgument("args");
        tail.setOptional(true);
        return tail;
    }

    private static String[] tailArgs(CommandArguments args) {
        String raw = (String) args.getOptional("args").orElse("");
        if (raw == null || raw.isBlank()) return NO_ARGS;
        return raw.trim().split("\\s+");
    }

    private static String[] suggestFromCompleter(PvPCorePlugin plugin, TabCompleter completer, String sub,
                                                 CommandSender sender, String currentArg) {
        String raw = currentArg == null ? "" : currentArg;
        boolean trailing = raw.endsWith(" ");
        String trimmed = trailing ? raw.substring(0, raw.length() - 1) : raw;
        List<String> tail = new ArrayList<>();
        if (trimmed.isEmpty()) tail.add("");
        else {
            tail.addAll(Arrays.asList(trimmed.split("\\s+")));
            if (trailing) tail.add("");
        }
        String[] arr = tail.toArray(new String[0]);
        List<String> result = completer.onTabComplete(sender, pvpcoreCommand(plugin), sub, arr);
        if (result == null || result.isEmpty()) return new String[0];
        return result.toArray(new String[0]);
    }

    private static Command pvpcoreCommand(PvPCorePlugin plugin) {
        Command cmd = plugin.getCommand("pvp-core");
        if (cmd != null) return cmd;
        throw new IllegalStateException("pvp-core command is not registered in plugin.yml");
    }

    public static void registerBukkitCommands(PvPCorePlugin plugin) {
        registerBukkitCommands(plugin, plugin.kitsContext());
    }

    public static void registerBukkitCommands(PvPCorePlugin plugin, KitsContext kits) {
        setActiveKits(kits);
        KitSlotTabCompleter kitSlotTabCompleter = new KitSlotTabCompleter();

        var kitCmd = cmd(plugin, "kit");
        if (kitCmd != null) kitCmd.setExecutor(new MainMenuCommand(plugin));

        var shareKitCmd = cmd(plugin, "sharekit");
        if (shareKitCmd != null) {
            shareKitCmd.setExecutor(new ShareKitCommand());
            shareKitCmd.setTabCompleter(new KitSlotTabCompleter(true));
        }

        var shareEcCmd = cmd(plugin, "shareec");
        if (shareEcCmd != null) {
            shareEcCmd.setExecutor(new ShareECKitCommand());
            shareEcCmd.setTabCompleter(new ECSlotTabCompleter(true));
        }

        var copyKitCmd = cmd(plugin, "copykit");
        if (copyKitCmd != null) copyKitCmd.setExecutor(new CopyKitCommand());

        TransferKitsCommand transferKitsCommand = new TransferKitsCommand();
        var transferKitsCmd = cmd(plugin, "transferkits");
        if (transferKitsCmd != null) {
            transferKitsCmd.setExecutor(transferKitsCommand);
            transferKitsCmd.setTabCompleter(transferKitsCommand);
        }

        ShareAcceptCommand shareAcceptCommand = new ShareAcceptCommand();
        var shareAcceptCmd = cmd(plugin, "shareaccept");
        if (shareAcceptCmd != null) {
            shareAcceptCmd.setExecutor(shareAcceptCommand);
            shareAcceptCmd.setTabCompleter(shareAcceptCommand);
        }

        ShareDeclineCommand shareDeclineCommand = new ShareDeclineCommand();
        var shareDeclineCmd = cmd(plugin, "sharedecline");
        if (shareDeclineCmd != null) {
            shareDeclineCmd.setExecutor(shareDeclineCommand);
            shareDeclineCmd.setTabCompleter(shareDeclineCommand);
        }

        KitRoomCommand kitRoomCommand = new KitRoomCommand();
        var kitRoomCmd = cmd(plugin, "kitroom");
        if (kitRoomCmd != null) {
            kitRoomCmd.setExecutor(kitRoomCommand);
            kitRoomCmd.setTabCompleter(kitRoomCommand);
        }

        var swapKitCmd = cmd(plugin, "swapkit");
        if (swapKitCmd != null) {
            swapKitCmd.setExecutor(new SwapKitCommand());
            swapKitCmd.setTabCompleter(kitSlotTabCompleter);
        }

        var deleteKitCmd = cmd(plugin, "deletekit");
        if (deleteKitCmd != null) {
            deleteKitCmd.setExecutor(new DeleteKitCommand());
            deleteKitCmd.setTabCompleter(kitSlotTabCompleter);
        }
        var deleteEcCmd = cmd(plugin, "deleteec");
        if (deleteEcCmd != null) {
            deleteEcCmd.setExecutor(new DeleteKitCommand(true));
            deleteEcCmd.setTabCompleter(kitSlotTabCompleter);
        }

        var inspectKitCmd = cmd(plugin, "inspectkit");
        if (inspectKitCmd != null) {
            InspectKitCommand inspectKit = new InspectKitCommand(plugin);
            inspectKitCmd.setExecutor(inspectKit);
            inspectKitCmd.setTabCompleter(inspectKit);
        }

        var inspectEcCmd = cmd(plugin, "inspectec");
        if (inspectEcCmd != null) {
            InspectEcCommand inspectEc = new InspectEcCommand(plugin);
            inspectEcCmd.setExecutor(inspectEc);
            inspectEcCmd.setTabCompleter(inspectEc);
        }

        var enderchestCmd = cmd(plugin, "enderchest");
        if (enderchestCmd != null) enderchestCmd.setExecutor(new EnderchestCommand());

        SavePublicKitCommand savePublicKitCommand = new SavePublicKitCommand();
        var savePublicKitCmd = cmd(plugin, "savepublickit");
        if (savePublicKitCmd != null) {
            savePublicKitCmd.setExecutor(savePublicKitCommand);
            savePublicKitCmd.setTabCompleter(savePublicKitCommand);
        }

        PublicKitCommand publicKitCommand = new PublicKitCommand(plugin);
        var publicKitCmd = cmd(plugin, "publickit");
        if (publicKitCmd != null) {
            publicKitCmd.setExecutor(publicKitCommand);
            publicKitCmd.setTabCompleter(publicKitCommand);
        }

        ShortcutCommandRegistrar.registerAll(plugin);

        RegearCommand regearCommand = new RegearCommand(plugin);
        if (kits != null) kits.setRegearCommand(regearCommand);
        var regearCmd = cmd(plugin, "regear");
        if (regearCmd != null) {
            regearCmd.setExecutor(regearCommand);
            Bukkit.getPluginManager().registerEvents(regearCommand, plugin);
        }

        var healCmd = cmd(plugin, "heal");
        if (healCmd != null) healCmd.setExecutor(new HealCommand());
        var repairCmd = cmd(plugin, "repair");
        if (repairCmd != null) repairCmd.setExecutor(new RepairCommand());
        var perPlayerKitCmd = cmd(plugin, "perplayerkit");
        if (perPlayerKitCmd != null) perPlayerKitCmd.setExecutor(new PerPlayerKitCommand(plugin));

        PurgeItemCommand purgeItemCommand = new PurgeItemCommand(plugin);
        var purgeItemCmd = cmd(plugin, "purgeitem");
        if (purgeItemCmd != null) {
            purgeItemCmd.setExecutor(purgeItemCommand);
            purgeItemCmd.setTabCompleter(purgeItemCommand);
        }

        KitDataCommand kitDataCommand = new KitDataCommand(plugin);
        var kitDataCmd = cmd(plugin, "kitdata");
        if (kitDataCmd != null) {
            kitDataCmd.setExecutor(kitDataCommand);
            kitDataCmd.setTabCompleter(kitDataCommand);
        }

        AboutCommandListener aboutListener = new AboutCommandListener();
        var aboutCmd = cmd(plugin, "aboutperplayerkit");
        if (aboutCmd != null) aboutCmd.setExecutor(aboutListener);
        Bukkit.getPluginManager().registerEvents(aboutListener, plugin);
    }

    public static void reloadBukkitCommands(PvPCorePlugin plugin) {
        reloadBukkitCommands(plugin, plugin.kitsContext());
    }

    public static void reloadBukkitCommands(PvPCorePlugin plugin, KitsContext kits) {
        setActiveKits(kits);
        ShortcutCommandRegistrar.unregisterAll(plugin);
        ShortcutCommandRegistrar.registerAll(plugin);

        RegearCommand previous = kits != null ? kits.regearCommand() : null;
        if (previous != null) {
            org.bukkit.event.HandlerList.unregisterAll(previous);
        }
        RegearCommand regearCommand = new RegearCommand(plugin);
        if (kits != null) kits.setRegearCommand(regearCommand);
        PluginCommand regear = plugin.getCommand("regear");
        if (regear != null) {
            regear.setExecutor(regearCommand);
            Bukkit.getPluginManager().registerEvents(regearCommand, plugin);
        } else {
            plugin.getLogger().warning("Command 'regear' missing from plugin.yml; /regear executor not refreshed.");
        }
    }

    public static void disableBukkitCommands(PvPCorePlugin plugin) {
        disableBukkitCommands(plugin, plugin.kitsContext());
    }

    public static void disableBukkitCommands(PvPCorePlugin plugin, KitsContext kits) {
        ShortcutCommandRegistrar.unregisterAll(plugin);
        RegearCommand previous = kits != null ? kits.regearCommand() : null;
        if (previous != null) {
            org.bukkit.event.HandlerList.unregisterAll(previous);
            kits.setRegearCommand(null);
        }
        if (activeKits == kits) {
            activeKits = null;
        }
    }

    private static PluginCommand cmd(PvPCorePlugin plugin, String name) {
        PluginCommand command = plugin == null ? null : plugin.getCommand(name);
        if (command == null && plugin != null) {
            plugin.getLogger().warning("Kits: command '" + name + "' is missing from plugin.yml; skipping registration.");
        }
        return command;
    }

    public static boolean disabled(CommandSender sender) {
        KitsContext kits = activeKits;
        if (kits != null && kits.isEnabled()) return false;
        if (kits != null && kits.lang() != null) kits.lang().sendNoPrefix(sender, "error.module-disabled");
        else sender.sendMessage("Kits are currently disabled.");
        return true;
    }
}
