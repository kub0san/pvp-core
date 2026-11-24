package org.pvpcore.commands.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.pvpcore.PvPCorePlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the /pvp-core help tree. Subcommands are grouped by category so the
 * top-level output fits on one screen; /pvp-core help &lt;sub&gt; shows usage and
 * description for a single subcommand, and /pvp-core help &lt;category&gt; shows only
 * that category.
 *
 * <p>Every line in the top-level and category output is clickable and suggests
 * the full usage of the subcommand.</p>
 */
public final class PvPCoreHelp {

    private record Spec(String name, String category, String usage, String description, String permission) {}

    private static final List<Spec> SPECS = List.of(
            // Core
            new Spec("help", "Core", "/pvp-core help [sub]", "Show this help, or detail for one subcommand.", null),
            new Spec("list", "Core", "/pvp-core list", "List mechanics and their current state.", "pvpcore.use"),
            new Spec("toggle", "Core", "/pvp-core toggle <mechanic>", "Flip a mechanic on or off.", "pvpcore.toggle"),
            new Spec("enable", "Core", "/pvp-core enable <mechanic>", "Enable a mechanic.", "pvpcore.toggle"),
            new Spec("disable", "Core", "/pvp-core disable <mechanic>", "Disable a mechanic.", "pvpcore.toggle"),
            new Spec("reload", "Core", "/pvp-core reload", "Reload messages, mechanics, worldgen, teleportation, kits and combat.", "pvpcore.reload"),


            // Kits â€” player
            new Spec("kit", "Kits", "/pvp-core kit [slot|settings]", "Open the kit menu, load a saved kit slot, or open the plugin settings.", "pvpcore.kits.menu"),
            new Spec("ec", "Kits", "/pvp-core ec [slot]", "Open the ender chest viewer, or load an ender chest slot.", "pvpcore.kits.viewenderchest"),
            new Spec("publickit", "Kits", "/pvp-core publickit [id]", "Open the public kit menu, or load a public kit by id.", "pvpcore.kits.publickit"),
            new Spec("sharekit", "Kits", "/pvp-core sharekit <slot> [player]", "Share a kit as a code, or request it from a player.", "pvpcore.kits.sharekit"),
            new Spec("shareec", "Kits", "/pvp-core shareec <slot> [player]", "Share an ender chest as a code, or request it from a player.", "pvpcore.kits.shareenderchest"),
            new Spec("shareaccept", "Kits", "/pvp-core shareaccept [id]", "Accept a pending share request.", "pvpcore.kits.copykit"),
            new Spec("sharedecline", "Kits", "/pvp-core sharedecline [id]", "Decline a pending share request.", "pvpcore.kits.copykit"),
            new Spec("copykit", "Kits", "/pvp-core copykit <code>", "Copy a shared kit or ender chest by code.", "pvpcore.kits.copykit"),
            new Spec("transferkits", "Kits", "/pvp-core transferkits <player>", "Request to transfer all your kits and ender chests.", "pvpcore.kits.transferkits"),
            new Spec("swapkit", "Kits", "/pvp-core swapkit <slot1> <slot2>", "Swap two of your saved kit slots.", "pvpcore.kits.swapkit"),
            new Spec("deletekit", "Kits", "/pvp-core deletekit <slot>", "Delete a saved kit slot.", "pvpcore.kits.deletekit"),
            new Spec("deleteec", "Kits", "/pvp-core deleteec <slot>", "Delete a saved ender chest slot.", "pvpcore.kits.deleteenderchest"),
            new Spec("regear", "Kits", "/pvp-core regear", "Regear to your last loaded kit.", "pvpcore.kits.regear"),
            new Spec("heal", "Kits", "/pvp-core heal", "Heal yourself.", "pvpcore.kits.heal"),
            new Spec("repair", "Kits", "/pvp-core repair", "Repair your gear.", "pvpcore.kits.repair"),

            // Kits â€” admin
            new Spec("kitroom", "Kits (admin)", "/pvp-core kitroom <load|save>", "Manage the kit room.", "pvpcore.kits.admin"),
            new Spec("savepublickit", "Kits (admin)", "/pvp-core savepublickit <id>", "Save your inventory as a public kit.", "pvpcore.kits.admin"),
            new Spec("inspectkit", "Kits (admin)", "/pvp-core inspectkit <player> <slot>", "Inspect a player's saved kit.", "pvpcore.kits.staff"),
            new Spec("inspectec", "Kits (admin)", "/pvp-core inspectec <player> <slot>", "Inspect a player's saved ender chest.", "pvpcore.kits.staff"),
            new Spec("purgeitem", "Kits (admin)", "/pvp-core purgeitem <item> <all confirm|player ...>", "Purge an item type from stored kits.", "pvpcore.kits.admin"),
            new Spec("kitdata", "Kits (admin)", "/pvp-core kitdata <export|import> ...", "Export or import kit data to a .ppk file.", "pvpcore.kits.admin"),
            new Spec("perplayerkit", "Kits (admin)", "/pvp-core perplayerkit <sub>", "Kits admin: autosetup, migrate, import, location, about.", "pvpcore.kits.admin"),
            new Spec("aboutperplayerkit", "Kits (admin)", "/pvp-core aboutperplayerkit", "Version and licence info.", null),

            // Teleportation
            new Spec("tpa", "Teleportation", "/pvp-core tpa <player>", "Request to teleport to another player.", "pvpcore.teleportation.tpa"),
            new Spec("tpahere", "Teleportation", "/pvp-core tpahere <player>", "Request another player to teleport to you.", "pvpcore.teleportation.tpahere"),
            new Spec("tpaccept", "Teleportation", "/pvp-core tpaccept", "Accept a teleport request.", "pvpcore.teleportation.tpaccept"),
            new Spec("tpdeny", "Teleportation", "/pvp-core tpdeny", "Deny a teleport request.", "pvpcore.teleportation.tpdeny"),
            new Spec("tpcancel", "Teleportation", "/pvp-core tpcancel", "Cancel your outgoing request.", "pvpcore.teleportation.tpcancel"),
            new Spec("tphere", "Teleportation", "/pvp-core tphere <player>", "Instantly teleport a player to you.", "pvpcore.teleportation.tphere"),
            new Spec("back", "Teleportation", "/pvp-core back", "Return to your last location.", "pvpcore.teleportation.back"),
            new Spec("spawn", "Teleportation", "/pvp-core spawn", "Teleport to the server spawn.", "pvpcore.teleportation.spawn"),
            new Spec("setspawn", "Teleportation", "/pvp-core setspawn", "Set the server spawn to your location.", "pvpcore.teleportation.setspawn"),
            new Spec("rtp", "Teleportation", "/pvp-core rtp [region]", "Teleport to a random safe location.", "pvpcore.teleportation.rtp")
    );

    private static final Map<String, List<Spec>> BY_CATEGORY = new LinkedHashMap<>();
    private static final Map<String, Spec> BY_NAME = new LinkedHashMap<>();
    static {
        for (Spec spec : SPECS) {
            BY_CATEGORY.computeIfAbsent(spec.category(), k -> new ArrayList<>()).add(spec);
            BY_NAME.put(spec.name(), spec);
        }
    }

    private final PvPCorePlugin plugin;

    public PvPCoreHelp(PvPCorePlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> knownSubcommands() {
        return new ArrayList<>(BY_NAME.keySet());
    }

    public boolean printTopLevel(CommandSender sender, String label) {
        Component prefix = plugin.messages().get("prefix");
        sender.sendMessage(prefix.append(Component.text("Commands", NamedTextColor.GOLD, TextDecoration.BOLD)));
        for (Map.Entry<String, List<Spec>> entry : BY_CATEGORY.entrySet()) {
            List<Spec> visible = new ArrayList<>();
            for (Spec spec : entry.getValue()) {
                if (maySee(sender, spec)) {
                    visible.add(spec);
                }
            }
            if (visible.isEmpty()) {
                continue;
            }
            sender.sendMessage(Component.text("  " + entry.getKey(), NamedTextColor.YELLOW, TextDecoration.BOLD));
            for (Spec spec : visible) {
                sender.sendMessage(line(spec));
            }
        }
        sender.sendMessage(Component.text("  Run ", NamedTextColor.GRAY)
                .append(Component.text("/" + label + " help <sub>", NamedTextColor.AQUA))
                .append(Component.text(" for details on one subcommand.", NamedTextColor.GRAY)));
        return true;
    }

    public boolean print(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            return printTopLevel(sender, label);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Spec spec = BY_NAME.get(sub);
        if (spec == null) {
            List<Spec> category = BY_CATEGORY.get(args[0]);
            if (category != null) {
                Component prefix = plugin.messages().get("prefix");
                sender.sendMessage(prefix.append(Component.text(args[0], NamedTextColor.GOLD, TextDecoration.BOLD)));
                for (Spec entry : category) {
                    if (maySee(sender, entry)) {
                        sender.sendMessage(line(entry));
                    }
                }
                return true;
            }
            sender.sendMessage(plugin.messages().get("unknown-subcommand", "label", label));
            return true;
        }
        if (!maySee(sender, spec)) {
            sender.sendMessage(plugin.messages().get("no-permission"));
            return true;
        }
        sender.sendMessage(plugin.messages().get("prefix").append(
                Component.text(spec.name(), NamedTextColor.GOLD, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("  Usage: ", NamedTextColor.GRAY)
                .append(Component.text(spec.usage(), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("  " + spec.description(), NamedTextColor.WHITE));
        if (spec.permission() != null) {
            sender.sendMessage(Component.text("  Permission: ", NamedTextColor.GRAY)
                    .append(Component.text(spec.permission(), NamedTextColor.DARK_GRAY)));
        }
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Spec spec : SPECS) {
            if (spec.name().startsWith(prefix) && maySee(sender, spec)) {
                result.add(spec.name());
            }
        }
        for (String category : BY_CATEGORY.keySet()) {
            if (category.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(category);
            }
        }
        return result;
    }

    private Component line(Spec spec) {
        return Component.text("    " + spec.usage(), NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand(spec.usage()))
                .hoverEvent(HoverEvent.showText(Component.text(spec.description(), NamedTextColor.GRAY)))
                .append(Component.text("  " + spec.description(), NamedTextColor.GRAY));
    }

    private static boolean maySee(CommandSender sender, Spec spec) {
        return spec.permission() == null || sender.hasPermission(spec.permission());
    }
}