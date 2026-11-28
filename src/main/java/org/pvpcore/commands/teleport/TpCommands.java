package org.pvpcore.commands.teleport;

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
import org.bukkit.entity.Player;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.TeleportService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Global command registration wiring for the teleport module.
 *
 * <p>Registers legacy Bukkit executors for plugin.yml-declared teleport commands
 * (exact behavior preserved) and provides native CommandAPI /pvp-core teleport
 * subcommands that delegate to existing teleport logic/router behavior.</p>
 */
public final class TpCommands {

    private static final List<String> TELEPORT_SUBS = List.of(
            "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel",
            "tphere", "back", "spawn", "setspawn", "rtp", "rtpq");

    private static final List<String> BUKKIT_NAMES = List.of(
            "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tphere", "back", "spawn", "setspawn", "rtp", "rtpq");

    private TpCommands() {
    }

    public static void register(PvPCorePlugin plugin, TeleportModule teleportation) {
        // Bukkit wiring is performed by TeleportModule lifecycle via registerBukkitCommands.
    }

    public static TeleportCommands registerBukkitCommands(org.bukkit.plugin.java.JavaPlugin plugin, TeleportModule module, TeleportService service) {
        TeleportCommands commands = new TeleportCommands(module, service);
        for (String name : BUKKIT_NAMES) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                plugin.getLogger().warning("teleportation: command '" + name + "' is missing from plugin.yml.");
                continue;
            }
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
        return commands;
    }

    public static List<CommandAPICommand> subcommands(PvPCorePlugin plugin) {
        List<CommandAPICommand> subs = new ArrayList<>();
        subs.add(playerTarget(plugin, "tpa"));
        subs.add(playerTarget(plugin, "tpahere"));
        subs.add(noArgs(plugin, "tpaccept"));
        subs.add(noArgs(plugin, "tpdeny"));
        subs.add(noArgs(plugin, "tpcancel"));
        subs.add(playerTarget(plugin, "tphere"));
        subs.add(noArgs(plugin, "back"));
        subs.add(noArgs(plugin, "spawn"));
        subs.add(noArgs(plugin, "setspawn"));
        subs.add(rtp(plugin));
        subs.add(rtpq(plugin));
        return subs;
    }

    public static boolean isTeleportSub(String sub) {
        return sub != null && TELEPORT_SUBS.contains(sub.toLowerCase(Locale.ROOT));
    }

    private static CommandAPICommand noArgs(PvPCorePlugin plugin, String sub) {
        return new CommandAPICommand(sub)
                .executes((CommandExecutor) (sender, args) -> execute(plugin, sender, sub, new String[0]))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> execute(plugin, sender, sub, new String[0]));
    }

    private static CommandAPICommand playerTarget(PvPCorePlugin plugin, String sub) {
        StringArgument player = new StringArgument("player");
        player.replaceSuggestions(ArgumentSuggestions.strings(info -> {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(info.sender())) names.add(online.getName());
            }
            return names.toArray(new String[0]);
        }));
        return new CommandAPICommand(sub)
                .withOptionalArguments(player)
                .executes((CommandExecutor) (sender, args) -> exec(plugin, sender, sub, args, "player"))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> exec(plugin, sender, sub, args, "player"));
    }

    private static CommandAPICommand rtp(PvPCorePlugin plugin) {
        GreedyStringArgument tail = new GreedyStringArgument("args");
        tail.setOptional(true);
        tail.replaceSuggestions(ArgumentSuggestions.strings(info ->
                suggest(plugin, "rtp", info.sender(), info.currentArg())));
        return new CommandAPICommand("rtp")
                .withOptionalArguments(tail)
                .executes((CommandExecutor) (sender, args) -> execTail(plugin, sender, "rtp", args))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> execTail(plugin, sender, "rtp", args));
    }

    private static CommandAPICommand rtpq(PvPCorePlugin plugin) {
        GreedyStringArgument tail = new GreedyStringArgument("args");
        tail.setOptional(true);
        tail.replaceSuggestions(ArgumentSuggestions.strings(info ->
                suggest(plugin, "rtpq", info.sender(), info.currentArg())));
        return new CommandAPICommand("rtpq")
                .withOptionalArguments(tail)
                .executes((CommandExecutor) (sender, args) -> execTail(plugin, sender, "rtpq", args))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> execTail(plugin, sender, "rtpq", args));
    }

    private static void exec(PvPCorePlugin plugin, CommandSender sender, String sub, CommandArguments args, String node) {
        String value = (String) args.getOptional(node).orElse(null);
        if (value == null || value.isBlank()) {
            execute(plugin, sender, sub, new String[0]);
        } else {
            execute(plugin, sender, sub, new String[]{value});
        }
    }

    private static void execTail(PvPCorePlugin plugin, CommandSender sender, String sub, CommandArguments args) {
        String raw = (String) args.getOptional("args").orElse("");
        if (raw == null || raw.isBlank()) {
            execute(plugin, sender, sub, new String[0]);
        } else {
            List<String> parts = new ArrayList<>();
            parts.addAll(Arrays.asList(raw.trim().split("\\s+")));
            execute(plugin, sender, sub, parts.toArray(new String[0]));
        }
    }

    private static void execute(PvPCorePlugin plugin, CommandSender sender, String sub, String[] tail) {
        TeleportModule module = plugin.teleportation();
        if (module == null || module.commands() == null) {
            sender.sendMessage(plugin.messages().get("prefix").append(
                    net.kyori.adventure.text.Component.text("Teleportation is not available.",
                            net.kyori.adventure.text.format.NamedTextColor.RED)));
            return;
        }
        Command bukkit = plugin.getCommand(sub);
        if (bukkit == null) {
            sender.sendMessage(plugin.messages().get("prefix").append(
                    net.kyori.adventure.text.Component.text("Teleportation is not available.",
                            net.kyori.adventure.text.format.NamedTextColor.RED)));
            return;
        }
        module.commands().onCommand(sender, bukkit, sub, tail);
    }

    private static String[] suggest(PvPCorePlugin plugin, String sub, CommandSender sender, String currentArg) {
        TeleportModule module = plugin.teleportation();
        if (module == null || module.commands() == null) return new String[0];
        Command bukkit = plugin.getCommand(sub);
        if (bukkit == null) return new String[0];

        String raw = currentArg == null ? "" : currentArg;
        boolean trailing = raw.endsWith(" ");
        String trimmed = trailing ? raw.substring(0, raw.length() - 1) : raw;
        List<String> tail = new ArrayList<>();
        if (trimmed.isEmpty()) tail.add("");
        else {
            tail.addAll(Arrays.asList(trimmed.split("\\s+")));
            if (trailing) tail.add("");
        }
        List<String> completions = module.commands().onTabComplete(sender, bukkit, sub, tail.toArray(new String[0]));
        return completions == null ? new String[0] : completions.toArray(new String[0]);
    }
}
