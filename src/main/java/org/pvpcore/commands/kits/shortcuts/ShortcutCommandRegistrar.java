package org.pvpcore.commands.kits.shortcuts;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.commands.kits.KitsCommands;
import org.pvpcore.commands.kits.impl.shortcuts.ShortECCommand;
import org.pvpcore.commands.kits.impl.shortcuts.ShortKitCommand;
import org.pvpcore.features.kits.KitsContext;

import java.util.ArrayList;
import java.util.List;

/** Wires all k&lt;N&gt;/ec&lt;N&gt; shortcut commands: slots 1..9 are declared statically in plugin.yml, while slots above 9 (when max-kits is raised) are registered at runtime through the server CommandMap. */
public final class ShortcutCommandRegistrar {

    private static final int STATIC_COMMAND_SLOTS = 9;
    private static final List<Command> REGISTERED = new ArrayList<>();

    private ShortcutCommandRegistrar() {}

    public static void registerAll(JavaPlugin plugin) {
        CommandExecutor kitExecutor = new ShortKitCommand();
        CommandExecutor ecExecutor = new ShortECCommand();
        for (int i = 1; i <= STATIC_COMMAND_SLOTS; i++) {
            org.bukkit.command.PluginCommand k = plugin.getCommand("k" + i);
            org.bukkit.command.PluginCommand ec = plugin.getCommand("ec" + i);
            if (k != null) k.setExecutor(kitExecutor);
            else plugin.getLogger().warning("Shortcut command missing from plugin.yml: k" + i);
            if (ec != null) ec.setExecutor(ecExecutor);
            else plugin.getLogger().warning("Shortcut command missing from plugin.yml: ec" + i);
        }

        KitsContext kits = KitsCommands.activeKits();
        int maxKits = kits != null ? kits.maxKits() : 9;
        if (maxKits <= STATIC_COMMAND_SLOTS) {
            return;
        }

        CommandMap commandMap;
        try {
            commandMap = (CommandMap) plugin.getServer().getClass().getMethod("getCommandMap").invoke(plugin.getServer());
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().warning("Could not access the server command map; /k10+ and /ec10+ shortcut commands unavailable.");
            return;
        }

        org.bukkit.command.PluginCommand k1 = plugin.getCommand("k1");
        org.bukkit.command.PluginCommand ec1 = plugin.getCommand("ec1");
        String kitPermission = k1 != null ? k1.getPermission() : "perplayerkit.kit";
        String ecPermission = ec1 != null ? ec1.getPermission() : "perplayerkit.enderchest";
        for (int i = STATIC_COMMAND_SLOTS + 1; i <= maxKits; i++) {
            register(plugin, commandMap, new DynamicSlotCommand(plugin, "k" + i, "kit" + i, kitPermission, kitExecutor));
            register(plugin, commandMap, new DynamicSlotCommand(plugin, "ec" + i, "enderchest" + i, ecPermission, ecExecutor));
        }
    }

    private static void register(Plugin plugin, CommandMap commandMap, DynamicSlotCommand command) {
        if (!commandMap.register("perplayerkit", command)) {
            plugin.getLogger().warning("Command /" + command.getName() + " is already taken by another plugin.");
            return;
        }
        REGISTERED.add(command);
    }

    public static void unregisterAll(JavaPlugin plugin) {
        if (REGISTERED.isEmpty()) {
            return;
        }
        try {
            CommandMap commandMap = (CommandMap) plugin.getServer().getClass().getMethod("getCommandMap").invoke(plugin.getServer());
            for (Command command : REGISTERED) {
                command.unregister(commandMap);
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().warning("Could not unregister /k10+ and /ec10+ commands: " + e.getMessage());
        } finally {
            REGISTERED.clear();
        }
    }
}
