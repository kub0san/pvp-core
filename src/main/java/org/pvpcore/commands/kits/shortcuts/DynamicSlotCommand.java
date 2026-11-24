package org.pvpcore.commands.kits.shortcuts;

import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A slot shortcut command (e.g. /k10, /ec42) registered at runtime for kit
 * slots above 9, which cannot be declared statically in plugin.yml because the
 * count depends on the max-kits config option.
 */
public class DynamicSlotCommand extends Command implements PluginIdentifiableCommand {

    private final Plugin plugin;
    private final CommandExecutor delegate;

    public DynamicSlotCommand(Plugin plugin, String name, String alias, String permission, CommandExecutor delegate) {
        super(name);
        this.plugin = plugin;
        this.delegate = delegate;
        setAliases(List.of(alias));
        setPermission(permission);
        setUsage("/" + name);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!plugin.isEnabled()) {
            throw new CommandException("Cannot execute command '" + label + "' in plugin "
                    + plugin.getPluginMeta().getName() + " - plugin is disabled.");
        }
        if (!testPermission(sender)) {
            return true;
        }
        return delegate.onCommand(sender, this, label, args);
    }

    @Override
    public @NotNull Plugin getPlugin() {
        return plugin;
    }
}
