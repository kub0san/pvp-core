package org.pvpcore.commands.core;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.CommandExecutor;
import dev.jorel.commandapi.executors.ConsoleCommandExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.commands.worldgen.GencoreCommands;
import org.pvpcore.commands.kits.KitsCommands;
import org.pvpcore.commands.teleport.TpCommands;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.mechanic.MechanicRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * CommandAPI registration for the /pvp-core root command.
 *
 * <p>Core subcommands are registered natively. Subdomains (kits, teleport,
 * worldgen) register their /pvp-core subcommands via contributors.
 * Remaining subcommands (if any during migration) can be added as
 * compatibility pass-throughs to the legacy router.</p>
 */
public final class CoreCommands {

    private static final String PERMISSION_USE = "pvpcore.use";
    private static final String PERMISSION_TOGGLE = "pvpcore.toggle";
    private static final String PERMISSION_RELOAD = "pvpcore.reload";

    private static final Set<String> NATIVE = Set.of(
            "help", "list", "toggle", "enable", "disable", "reload"
    );

    private CoreCommands() {
    }

    public static void register(PvPCorePlugin plugin, PvPCoreHelp help) {
        register(plugin, help, plugin.kitsContext());
    }

    public static void register(PvPCorePlugin plugin, PvPCoreHelp help, org.pvpcore.features.kits.KitsContext kits) {
        CommandAPICommand root = new CommandAPICommand("pvp-core")
                .withAliases("pvpcore", "pc")
                .executes((CommandExecutor) (sender, args) -> help.printTopLevel(sender, "pvp-core"))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> help.printTopLevel(sender, "pvp-core"));

        Set<String> seen = new LinkedHashSet<>(NATIVE);

        root.withSubcommand(helpSub(plugin, help));
        root.withSubcommand(list(plugin));
        root.withSubcommand(mechanicAction(plugin, "toggle"));
        root.withSubcommand(mechanicAction(plugin, "enable"));
        root.withSubcommand(mechanicAction(plugin, "disable"));
        root.withSubcommand(reload(plugin));

        for (CommandAPICommand sub : TpCommands.subcommands(plugin)) {
            if (seen.add(sub.getName().toLowerCase(Locale.ROOT))) root.withSubcommand(sub);
        }
        for (CommandAPICommand sub : KitsCommands.subcommands(plugin, kits)) {
            if (seen.add(sub.getName().toLowerCase(Locale.ROOT))) root.withSubcommand(sub);
        }
        for (CommandAPICommand sub : GencoreCommands.subcommands()) {
            if (seen.add(sub.getName().toLowerCase(Locale.ROOT))) root.withSubcommand(sub);
        }

        root.register(plugin);
    }

    private static CommandAPICommand helpSub(PvPCorePlugin plugin, PvPCoreHelp help) {
        GreedyStringArgument tail = optionalTail();
        tail.replaceSuggestions(ArgumentSuggestions.strings(info -> {
            String[] t = splitTabArgs(info.currentArg() == null ? "" : info.currentArg());
            return help.tabComplete(info.sender(), t).toArray(new String[0]);
        }));
        return new CommandAPICommand("help")
                .withOptionalArguments(tail)
                .executes((CommandExecutor) (sender, args) -> help.print(sender, "pvp-core", tailArgs(args)))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> help.print(sender, "pvp-core", tailArgs(args)));
    }

    private static CommandAPICommand list(PvPCorePlugin plugin) {
        return new CommandAPICommand("list")
                .executes((CommandExecutor) (sender, args) -> handleList(plugin, sender))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> handleList(plugin, sender));
    }

    private static CommandAPICommand mechanicAction(PvPCorePlugin plugin, String action) {
        StringArgument mechanic = new StringArgument("mechanic");
        mechanic.replaceSuggestions(ArgumentSuggestions.strings(info -> mechanicIds(plugin)));
        return new CommandAPICommand(action)
                .withOptionalArguments(mechanic)
                .executes((CommandExecutor) (sender, args) -> {
                    String id = (String) args.getOptional("mechanic").orElse(null);
                    handleToggle(plugin, sender, action, id == null ? new String[0] : new String[]{id});
                })
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> {
                    String id = (String) args.getOptional("mechanic").orElse(null);
                    handleToggle(plugin, sender, action, id == null ? new String[0] : new String[]{id});
                });
    }

    private static CommandAPICommand reload(PvPCorePlugin plugin) {
        return new CommandAPICommand("reload")
                .executes((CommandExecutor) (sender, args) -> handleReload(plugin, sender))
                .executesConsole((ConsoleCommandExecutor) (sender, args) -> handleReload(plugin, sender));
    }

    public static void handleList(PvPCorePlugin plugin, CommandSender sender) {
        if (!has(plugin, sender, PERMISSION_USE)) return;
        sender.sendMessage(plugin.messages().get("mechanics-header"));
        for (Mechanic mechanic : plugin.mechanics().all()) {
            boolean on = mechanic.isEnabled();
            Component state = Component.text(on ? "[ON]" : "[OFF]", on ? NamedTextColor.GREEN : NamedTextColor.RED);
            org.pvpcore.mechanic.MechanicStatus status = plugin.mechanics().status(mechanic);
            String reason = status != null ? status.reason() : null;
            String note = (reason != null && !reason.isBlank()) ? reason : mechanic.statusNote();
            Component line = Component.text("  " + mechanic.id(), NamedTextColor.AQUA)
                    .append(Component.text(" " + mechanic.displayName(), NamedTextColor.WHITE))
                    .append(Component.text(" ")).append(state)
                    .append(Component.text(" - " + mechanic.description(), NamedTextColor.DARK_GRAY));
            if (note != null && !note.isEmpty()) line = line.append(Component.text(" (" + note + ")", NamedTextColor.YELLOW));
            sender.sendMessage(line);
        }
    }

    public static void handleToggle(PvPCorePlugin plugin, CommandSender sender, String sub, String[] tail) {
        if (!has(plugin, sender, PERMISSION_TOGGLE)) return;
        if (tail.length < 1) {
            sender.sendMessage(plugin.messages().get("usage", "usage", "/pvp-core " + sub + " <mechanic>"));
            return;
        }
        MechanicRegistry mechanics = plugin.mechanics();
        MechanicRegistry.Result result = sub.equals("toggle") ? mechanics.toggle(tail[0]) : mechanics.set(tail[0], sub.equals("enable"));
        sender.sendMessage(plugin.messages().get("prefix").append(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    public static void handleReload(PvPCorePlugin plugin, CommandSender sender) {
        if (!has(plugin, sender, PERMISSION_RELOAD)) return;
        runReloadStep(plugin, "plugin config", plugin::reloadConfig);
        runReloadStep(plugin, "messages", plugin::reloadMessages);
        runReloadStep(plugin, "kits", () -> {
            if (plugin.kits() != null) plugin.kits().reloadModule();
        });
        runReloadStep(plugin, "mechanics", () -> {
            if (plugin.mechanics() != null) plugin.mechanics().reload();
        });
        runReloadStep(plugin, "worldgen", () -> {
            if (plugin.generation() != null) plugin.generation().reload();
        });
        runReloadStep(plugin, "teleportation", () -> {
            if (plugin.teleportation() != null) plugin.teleportation().reload();
        });
        runReloadStep(plugin, "combat", () -> {
            if (plugin.combat() != null) plugin.combat().reload();
        });
        sender.sendMessage(plugin.messages().get("reloaded"));
    }

    private static void runReloadStep(PvPCorePlugin plugin, String step, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Reload step failed: " + step, t);
        }
    }

    private static boolean has(PvPCorePlugin plugin, CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(plugin.messages().get("no-permission"));
        return false;
    }

    private static String[] mechanicIds(PvPCorePlugin plugin) {
        if (plugin.mechanics() == null) return new String[0];
        List<String> ids = new ArrayList<>();
        for (Mechanic mechanic : plugin.mechanics().all()) ids.add(mechanic.id());
        return ids.toArray(new String[0]);
    }

    private static GreedyStringArgument optionalTail() {
        GreedyStringArgument tail = new GreedyStringArgument("args");
        tail.setOptional(true);
        return tail;
    }

    private static String[] tailArgs(CommandArguments args) {
        String raw = (String) args.getOptional("args").orElse("");
        return splitExecutionArgs(raw);
    }

    private static String[] splitExecutionArgs(String raw) {
        if (raw == null || raw.isBlank()) return new String[0];
        return raw.trim().split("\\s+");
    }

    private static String[] splitTabArgs(String raw) {
        boolean trailingSpace = raw.endsWith(" ");
        String trimmed = trailingSpace ? raw.substring(0, raw.length() - 1) : raw;
        if (trimmed.isEmpty()) return new String[]{""};
        List<String> tokens = new ArrayList<>(Arrays.asList(trimmed.split("\\s+")));
        if (trailingSpace) tokens.add("");
        return tokens.toArray(new String[0]);
    }
}
