package org.pvpcore.commands.worldgen;

import dev.jorel.commandapi.CommandAPICommand;
import org.pvpcore.PvPCorePlugin;
import java.util.List;

/**
 * Global command registration wiring for the gencore module.
 *
 * <p>gencore currently exposes no /pvp-core subcommands; world generation is
 * configured via config.yml and Bukkit/world-manager generator ids.</p>
 */
public final class GencoreCommands {

    private GencoreCommands() {
    }

    public static void register(PvPCorePlugin plugin) {
    }

    public static List<CommandAPICommand> subcommands() {
        return List.of();
    }
}
