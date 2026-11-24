package org.pvpcore.commands.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pvpcore.PvPCorePlugin;
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
import org.pvpcore.commands.kits.impl.kits.SwapKitCommand;
import org.pvpcore.commands.kits.impl.share.CopyKitCommand;
import org.pvpcore.commands.kits.impl.share.ShareAcceptCommand;
import org.pvpcore.commands.kits.impl.share.ShareDeclineCommand;
import org.pvpcore.commands.kits.impl.share.ShareECKitCommand;
import org.pvpcore.commands.kits.impl.share.ShareKitCommand;
import org.pvpcore.commands.kits.impl.share.TransferKitsCommand;
import org.pvpcore.commands.kits.impl.shortcuts.ShortECCommand;
import org.pvpcore.commands.kits.impl.shortcuts.ShortKitCommand;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.gui.kits.GUI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Bukkit fallback dispatcher for /pvp-core when CommandAPI is not the active handler.
 * Mirrors the native command behavior for compatibility.
 */
public final class PvPCoreCommandFallback {

    private static final String[] NO_ARGS = new String[0];
    private static final List<String> TELEPORT_SUBS = List.of(
            "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel",
            "tphere", "back", "spawn", "setspawn", "rtp");

    private final PvPCorePlugin plugin;
    private final PvPCoreHelp help;
    private final KitsContext kits;

    private final MainMenuCommand kitMenu;
    private final ShortKitCommand kitSlot;
    private final ShortECCommand ecSlot;
    private final EnderchestCommand enderchestView;
    private final PublicKitCommand publicKit;
    private final KitRoomCommand kitRoom;
    private final HealCommand heal;
    private final RepairCommand repair;
    private final ShareKitCommand shareKit;
    private final ShareECKitCommand shareEc;
    private final ShareAcceptCommand shareAccept;
    private final ShareDeclineCommand shareDecline;
    private final CopyKitCommand copyKit;
    private final SwapKitCommand swapKit;
    private final DeleteKitCommand deleteKit;
    private final DeleteKitCommand deleteEc;
    private final InspectKitCommand inspectKit;
    private final InspectEcCommand inspectEc;
    private final TransferKitsCommand transferKits;
    private final SavePublicKitCommand savePublicKit;
    private final PurgeItemCommand purgeItem;
    private final KitDataCommand kitData;
    private final PerPlayerKitCommand perPlayerKit;
    private final AboutCommandListener about;

    private final KitSlotTabCompleter kitSlotPlayerCompleter = new KitSlotTabCompleter(true);
    private final ECSlotTabCompleter ecSlotPlayerCompleter = new ECSlotTabCompleter(true);

    public PvPCoreCommandFallback(PvPCorePlugin plugin, PvPCoreHelp help) {
        this(plugin, help, plugin.kitsContext());
    }

    public PvPCoreCommandFallback(PvPCorePlugin plugin, PvPCoreHelp help, KitsContext kits) {
        this.plugin = plugin;
        this.help = help;
        this.kits = kits;

        this.kitMenu = new MainMenuCommand(plugin);
        this.kitSlot = new ShortKitCommand();
        this.ecSlot = new ShortECCommand();
        this.enderchestView = new EnderchestCommand();
        this.publicKit = new PublicKitCommand(plugin);
        this.kitRoom = new KitRoomCommand();
        this.heal = new HealCommand();
        this.repair = new RepairCommand();
        this.shareKit = new ShareKitCommand();
        this.shareEc = new ShareECKitCommand();
        this.shareAccept = new ShareAcceptCommand();
        this.shareDecline = new ShareDeclineCommand();
        this.copyKit = new CopyKitCommand();
        this.swapKit = new SwapKitCommand();
        this.deleteKit = new DeleteKitCommand();
        this.deleteEc = new DeleteKitCommand(true);
        this.inspectKit = new InspectKitCommand(plugin);
        this.inspectEc = new InspectEcCommand(plugin);
        this.transferKits = new TransferKitsCommand();
        this.savePublicKit = new SavePublicKitCommand();
        this.purgeItem = new PurgeItemCommand(plugin);
        this.kitData = new KitDataCommand(plugin);
        this.perPlayerKit = new PerPlayerKitCommand(plugin);
        this.about = new AboutCommandListener();
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return help.printTopLevel(sender, label);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] tail = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : NO_ARGS;

        return switch (sub) {
            case "help" -> help.print(sender, label, tail);
            case "list" -> { CoreCommands.handleList(plugin, sender); yield true; }
            case "toggle", "enable", "disable" -> { CoreCommands.handleToggle(plugin, sender, sub, tail); yield true; }
            case "reload" -> { CoreCommands.handleReload(plugin, sender); yield true; }

            case "kit" -> handleKit(sender, label, tail);
            case "ec", "enderchest" -> handleEc(sender, label, tail);
            case "publickit" -> publicKit.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "kitroom" -> kitRoom.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "regear" -> handleRegear(sender, tail);
            case "heal" -> heal.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "repair" -> repair.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "sharekit" -> shareKit.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "shareec" -> shareEc.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "shareaccept" -> shareAccept.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "sharedecline" -> shareDecline.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "copykit" -> copyKit.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "swapkit" -> swapKit.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "deletekit" -> deleteKit.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "deleteec" -> deleteEc.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "inspectkit" -> inspectKit.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "inspectec" -> inspectEc.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "transferkits" -> transferKits.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "savepublickit" -> savePublicKit.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "purgeitem" -> purgeItem.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "kitdata" -> kitData.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "perplayerkit" -> perPlayerKit.onCommand(sender, pvpcoreCommand(), sub, tail);
            case "aboutperplayerkit" -> about.onCommand(sender, pvpcoreCommand(), sub, tail);
            default -> {
                if (TELEPORT_SUBS.contains(sub)) {
                    yield handleTeleport(sender, sub, tail);
                }
                sender.sendMessage(plugin.messages().get("unknown-subcommand", "label", label));
                yield true;
            }
        };
    }

    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length <= 1) {
            return filter(help.knownSubcommands(), args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 1, args.length);

        return switch (sub) {
            case "toggle", "enable", "disable" -> filter(mechanicIds(), tail[0]);
            case "kit" -> tail.length == 1 ? filter(slotRangeWithSettings(sender), tail[0]) : List.of();
            case "ec", "enderchest" -> tail.length == 1 ? filter(slotRange(), tail[0]) : List.of();
            case "swapkit" -> tail.length <= 2 ? filter(slotRange(), tail[tail.length - 1]) : List.of();
            case "deletekit", "deleteec" -> tail.length == 1 ? filter(slotRange(), tail[0]) : List.of();

            case "publickit" -> publicKit.onTabComplete(sender, pvpcoreCommand(), "publickit", tail);
            case "kitroom" -> kitRoom.onTabComplete(sender, pvpcoreCommand(), "kitroom", tail);
            case "sharekit" -> kitSlotPlayerCompleter.onTabComplete(sender, pvpcoreCommand(), "sharekit", tail);
            case "shareec" -> ecSlotPlayerCompleter.onTabComplete(sender, pvpcoreCommand(), "shareec", tail);
            case "shareaccept" -> shareAccept.onTabComplete(sender, pvpcoreCommand(), "shareaccept", tail);
            case "sharedecline" -> shareDecline.onTabComplete(sender, pvpcoreCommand(), "sharedecline", tail);
            case "inspectkit" -> inspectKit.onTabComplete(sender, pvpcoreCommand(), "inspectkit", tail);
            case "inspectec" -> inspectEc.onTabComplete(sender, pvpcoreCommand(), "inspectec", tail);
            case "transferkits" -> transferKits.onTabComplete(sender, pvpcoreCommand(), "transferkits", tail);
            case "savepublickit" -> savePublicKit.onTabComplete(sender, pvpcoreCommand(), "savepublickit", tail);
            case "purgeitem" -> purgeItem.onTabComplete(sender, pvpcoreCommand(), "purgeitem", tail);
            case "kitdata" -> kitData.onTabComplete(sender, pvpcoreCommand(), "kitdata", tail);
            case "perplayerkit" -> perPlayerKit.onTabComplete(sender, pvpcoreCommand(), "perplayerkit", tail);
            case "help" -> help.tabComplete(sender, tail);
            default -> {
                if (TELEPORT_SUBS.contains(sub)) {
                    yield teleportTabComplete(sender, sub, tail);
                }
                yield List.of();
            }
        };
    }

    private boolean handleKit(CommandSender sender, String label, String[] tail) {
        if (kitsDisabled(sender)) {
            return true;
        }
        if (tail.length == 0) return kitMenu.onCommand(sender, pvpcoreCommand(), label, NO_ARGS);
        if (tail.length == 1 && tail[0].equalsIgnoreCase("settings")) {
            Player player = requirePlayer(sender);
            if (player == null) return true;
            if (!player.hasPermission("perplayerkit.editkitroom") && !player.hasPermission("perplayerkit.admin")) {
                if (kits != null && kits.lang() != null) kits.lang().send(player, "error.no-permission");
                return true;
            }
            if (kits != null) GUI.getOrCreate(plugin, kits).OpenMainManagerMenu(player);
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) return true;
        int max = maxKits();
        Integer slot = SlotArgumentParser.parseSlotInRange(tail[0], 1, max);
        if (slot == null) {
            if (kits != null && kits.lang() != null) {
                kits.lang().send(player, "error.invalid-slot-range", "min", "1", "max", String.valueOf(max));
            }
            return true;
        }
        kitSlot.runForSlot(player, slot);
        return true;
    }

    private boolean handleEc(CommandSender sender, String label, String[] tail) {
        if (kitsDisabled(sender)) {
            return true;
        }
        if (tail.length == 0) return enderchestView.onCommand(sender, pvpcoreCommand(), label, NO_ARGS);
        Player player = requirePlayer(sender);
        if (player == null) return true;
        int max = maxKits();
        Integer slot = SlotArgumentParser.parseSlotInRange(tail[0], 1, max);
        if (slot == null) {
            if (kits != null && kits.lang() != null) {
                kits.lang().send(player, "error.invalid-slot-range", "min", "1", "max", String.valueOf(max));
            }
            return true;
        }
        ecSlot.runForSlot(player, slot);
        return true;
    }

    private boolean handleRegear(CommandSender sender, String[] tail) {
        RegearCommand regear = kits != null ? kits.regearCommand() : null;
        if (regear == null) {
            sender.sendMessage(plugin.messages().get("prefix").append(Component.text("Kits is disabled; /pvp-core regear is unavailable.", NamedTextColor.RED)));
            return true;
        }
        return regear.onCommand(sender, pvpcoreCommand(), "regear", tail);
    }

    private boolean kitsDisabled(CommandSender sender) {
        if (kits != null && kits.isEnabled()) return false;
        if (kits != null && kits.lang() != null) kits.lang().sendNoPrefix(sender, "error.module-disabled");
        else sender.sendMessage("Kits are currently disabled.");
        return true;
    }

    private int maxKits() {
        return kits != null ? kits.maxKits() : 9;
    }

    private boolean handleTeleport(CommandSender sender, String sub, String[] tail) {
        if (plugin.teleportation() == null || plugin.teleportation().commands() == null) {
            sender.sendMessage(plugin.messages().get("prefix").append(Component.text("Teleportation is not available.", NamedTextColor.RED)));
            return true;
        }
        Command target = plugin.getCommand(sub);
        if (target == null) {
            sender.sendMessage(plugin.messages().get("prefix").append(Component.text("Teleportation is not available.", NamedTextColor.RED)));
            return true;
        }
        return plugin.teleportation().commands().onCommand(sender, target, sub, tail);
    }

    private List<String> teleportTabComplete(CommandSender sender, String sub, String[] tail) {
        var target = plugin.getCommand(sub);
        if (plugin.teleportation() == null || plugin.teleportation().commands() == null || target == null) return List.of();
        List<String> result = plugin.teleportation().commands().onTabComplete(sender, target, sub, tail);
        return result == null ? List.of() : result;
    }

    private Command pvpcoreCommand() {
        return plugin.getCommand("pvp-core");
    }

    private @Nullable Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        if (kits != null && kits.lang() != null) kits.lang().sendNoPrefix(sender, "error.players-only");
        else sender.sendMessage("Only players can use this command.");
        return null;
    }

    private List<String> mechanicIds() {
        if (plugin.mechanics() == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (var mechanic : plugin.mechanics().all()) ids.add(mechanic.id());
        return ids;
    }

    private List<String> slotRange() {
        int max = maxKits();
        List<String> slots = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) slots.add(String.valueOf(i));
        return slots;
    }

    private List<String> slotRangeWithSettings(CommandSender sender) {
        List<String> options = new ArrayList<>(slotRange());
        if (sender instanceof Player p
                && (p.hasPermission("perplayerkit.editkitroom") || p.hasPermission("perplayerkit.admin"))) {
            options.add("settings");
        }
        return options;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>(options.size());
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) matches.add(option);
        }
        return matches;
    }
}
