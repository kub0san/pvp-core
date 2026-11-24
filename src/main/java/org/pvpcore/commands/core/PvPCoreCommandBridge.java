package org.pvpcore.commands.core;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.pvpcore.PvPCorePlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Forwards legacy top-level command names to their registered Bukkit command executors
 * so existing binds, docs and muscle memory keep working.
 */
public final class PvPCoreCommandBridge implements Listener {

    private static final Map<String, String> COMMAND_MAP = Map.ofEntries(
            // Kits — menu and direct commands
            Map.entry("kit", "kit"),
            Map.entry("k", "kit"),
            Map.entry("ec", "enderchest"),
            Map.entry("enderchest", "enderchest"),
            Map.entry("publickit", "publickit"),
            Map.entry("pk", "publickit"),
            Map.entry("premadekit", "publickit"),
            Map.entry("kitroom", "kitroom"),
            Map.entry("regear", "regear"),
            Map.entry("rg", "regear"),
            Map.entry("heal", "heal"),
            Map.entry("repair", "repair"),

            // Sharing
            Map.entry("sharekit", "sharekit"),
            Map.entry("shareec", "shareec"),
            Map.entry("shareenderchest", "shareec"),
            Map.entry("shareaccept", "shareaccept"),
            Map.entry("sharedecline", "sharedecline"),
            Map.entry("copykit", "copykit"),
            Map.entry("copyec", "copykit"),
            Map.entry("copyenderchest", "copykit"),
            Map.entry("swapkit", "swapkit"),
            Map.entry("deletekit", "deletekit"),
            Map.entry("deleteec", "deleteec"),
            Map.entry("deleteenderchest", "deleteec"),
            Map.entry("transferkits", "transferkits"),
            Map.entry("transferkit", "transferkits"),

            // Admin
            Map.entry("inspectkit", "inspectkit"),
            Map.entry("inspectec", "inspectec"),
            Map.entry("savepublickit", "savepublickit"),
            Map.entry("purgeitem", "purgeitem"),
            Map.entry("purgeitems", "purgeitem"),
            Map.entry("kitdata", "kitdata"),
            Map.entry("perplayerkit", "perplayerkit"),
            Map.entry("aboutperplayerkit", "aboutperplayerkit")
    );

    private final PvPCorePlugin plugin;

    public PvPCoreCommandBridge(PvPCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled()) return;
        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) return;

        String[] parsed = split(message.substring(1));
        String targetName = resolveTarget(parsed[0]);
        if (targetName == null) return;
        PluginCommand target = plugin.getCommand(targetName);
        if (target == null || target.getExecutor() == null) return;

        event.setCancelled(true);
        target.execute(event.getPlayer(), targetName, tail(parsed));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!enabled()) return;
        String raw = event.getCommand();
        if (raw == null || raw.isBlank()) return;

        if (raw.startsWith("/")) raw = raw.substring(1);
        String[] parsed = split(raw);
        String targetName = resolveTarget(parsed[0]);
        if (targetName == null) return;
        PluginCommand target = plugin.getCommand(targetName);
        if (target == null || target.getExecutor() == null) return;

        event.setCancelled(true);
        target.execute(event.getSender(), targetName, tail(parsed));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (!enabled()) return;
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) return;

        String[] parsed = split(buffer.substring(1));
        if (parsed.length == 0) return;

        String targetName = resolveTarget(parsed[0]);
        if (targetName == null) return;
        PluginCommand target = plugin.getCommand(targetName);
        if (target == null || target.getTabCompleter() == null) return;

        List<String> completions = target.getTabCompleter().onTabComplete(event.getSender(), target, targetName, tail(parsed));
        event.setCompletions(completions == null ? List.of() : completions);
    }

    private String resolveTarget(String token) {
        String normalized = stripNamespace(token);
        return COMMAND_MAP.get(normalized);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("command-bridge.enabled", true);
    }

    private static String[] split(String input) {
        String trimmed = input;
        boolean trailingSpace = trimmed.endsWith(" ");
        if (trailingSpace) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isEmpty()) return new String[] {""};

        List<String> tokens = new ArrayList<>(Arrays.asList(trimmed.split(" +", -1)));
        if (trailingSpace) tokens.add("");
        return tokens.toArray(new String[0]);
    }

    private static String stripNamespace(String token) {
        int colon = token.indexOf(':');
        String name = colon >= 0 ? token.substring(colon + 1) : token;
        return name.toLowerCase(Locale.ROOT);
    }

    private static String[] tail(String[] parsed) {
        if (parsed.length <= 1) return new String[0];
        return Arrays.copyOfRange(parsed, 1, parsed.length);
    }
}
