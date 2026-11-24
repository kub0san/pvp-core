package org.pvpcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.commands.core.CoreCommands;
import org.pvpcore.commands.core.PvPCoreCommandBridge;
import org.pvpcore.commands.core.PvPCoreCommandFallback;
import org.pvpcore.commands.core.PvPCoreHelp;
import org.pvpcore.features.combat.CombatTagFeature;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.worldgen.GenerationModule;
import org.pvpcore.mechanic.MechanicRegistry;

/**
 * Global command registration entry point for pvp-core.
 *
 * <p>Registers the legacy command bridge and the CommandAPI {@code /pvp-core}
 * ({@code /pc}) tree. Legacy top-level Bukkit command executors and shortcut
 * wiring remain owned by module lifecycles (kits/teleport).</p>
 */
public final class PvPCoreCommands {

    private final PvPCorePlugin plugin;
    private final PvPCoreHelp help;
    private final PvPCoreCommandBridge bridge;
    private final PvPCoreCommandFallback fallback;

    public PvPCoreCommands(PvPCorePlugin plugin, MechanicRegistry mechanics,
                           GenerationModule generation, TeleportModule teleportation,
                           CombatTagFeature combat) {
        this.plugin = plugin;
        this.help = new PvPCoreHelp(plugin);
        this.bridge = new PvPCoreCommandBridge(plugin);
        this.fallback = new PvPCoreCommandFallback(plugin, help, plugin.kitsContext());
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(bridge, plugin);
        CoreCommands.register(plugin, help, plugin.kitsContext());

        // Bukkit-side fallback for /pvp-core (and its aliases /pvpcore and /pc).
        PluginCommand root = plugin.getCommand("pvp-core");
        if (root != null) {
            root.setExecutor((sender, command, label, args) -> fallback.execute(sender, label, args));
            root.setTabCompleter((sender, command, label, args) -> fallback.tabComplete(sender, label, args));
        } else {
            plugin.getLogger().warning("Root command 'pvp-core' is missing from plugin.yml; /pvp-core fallback executor not registered.");
        }
    }
}
