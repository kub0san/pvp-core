/*
 * Copyright 2022-2026 Noah Ross
 *
 * This file is part of Kits.
 *
 * Kits is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Kits is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Kits. If not, see <https://www.gnu.org/licenses/>.
 */
package org.pvpcore.commands.kits.impl.admin;

import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.LocationAccess;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.RekitKitResolver;
import org.pvpcore.commands.kits.impl.core.CommandGuards;
import org.bukkit.entity.Player;
import org.pvpcore.util.kits.importutil.KitsXImporter;
import org.pvpcore.util.kits.importutil.LegacyDatabaseImporter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PerPlayerKitCommand implements CommandExecutor, TabCompleter {

    private static final List<String> STORAGE_TYPES = Arrays.asList("sqlite", "mysql", "postgresql", "redis", "yml");

    private final Plugin plugin;

    public PerPlayerKitCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (org.pvpcore.commands.kits.KitsCommands.disabled(sender)) {
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "location":
                return handleLocation(sender, args);
            case "about":
                org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "command.perplayerkit-about");
                return true;
            case "import":
                return handleImport(sender, args);
            case "migrate":
                return handleMigrate(sender, args);
            default:
                org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "error.invalid-subcommand");
                sendUsage(sender);
                return true;

        }
    }

    /** A bare /perplayerkit used to say "Missing arguments!" and leave it there. */
    private void sendUsage(CommandSender sender) {
        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "command.perplayerkit-usage-header");
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "command.perplayerkit-usage-autosetup");
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "command.perplayerkit-usage-about");
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "command.perplayerkit-usage-location");
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "command.perplayerkit-usage-import");
        org.pvpcore.commands.kits.KitsCommands.lang().sendNoPrefix(sender, "command.perplayerkit-usage-migrate");
    }

    private boolean handleLocation(CommandSender sender, String[] args) {
        Player player = CommandGuards.requirePlayer(sender);
        if (player == null) return true;
        if (args.length > 2) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "command.perplayerkit-location-usage");
            return true;
        }
        LocationFeature feature;
        try { feature = args.length == 2 ? LocationFeature.fromKey(args[1].toLowerCase(java.util.Locale.ROOT)) : LocationFeature.GLOBAL; }
        catch (IllegalArgumentException e) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "command.perplayerkit-location-usage");
            return true;
        }
        LocationAccess access = (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().locationAccess() : null);
        LocationAccess.Decision decision = access.check(player, feature);
        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "info.location-result", "world", player.getWorld().getName(),
                "feature", feature.key(), "result", decision.allowed() ? "ALLOW" : "DENY",
                "rule", decision.rule(), "reason", org.pvpcore.commands.kits.KitsCommands.lang().raw(decision.reasonKey()));
        List<String> regions = List.of();
        boolean regionsAvailable = true;
        try {
            regions = access.regions(player);
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "info.location-regions", "regions", regions.isEmpty() ? org.pvpcore.commands.kits.KitsCommands.lang().raw("info.location-no-regions") : String.join(", ", regions));
        } catch (LocationAccess.RegionUnavailableException e) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "info.location-regions", "regions", e.getMessage());
            regionsAvailable = false;
        }
        String path = switch (feature) {
            case REKIT_RESPAWN -> "rekit.respawn.kits";
            case REKIT_KILL -> "rekit.kill.kits";
            default -> null;
        };
        if (path != null) {
            var mappings = (org.pvpcore.commands.kits.KitsCommands.activeKits() != null ? org.pvpcore.commands.kits.KitsCommands.activeKits().config() : null).getConfigurationSection(path);
            String kit = !regionsAvailable && RekitKitResolver.hasRegionEntries(mappings, player.getWorld().getName())
                    ? org.pvpcore.commands.kits.KitsCommands.lang().raw("info.location-kit-unavailable")
                    : RekitKitResolver.resolveKit(mappings, player.getWorld().getName(), regions);
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "info.location-kit", "kit", kit == null ? org.pvpcore.commands.kits.KitsCommands.lang().raw("info.location-last-kit") : kit);
        }
        return true;
    }

    private boolean handleImport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "error.missing-import-type");
            return true;
        }

        // NEW: Legacy Database Import
        if (args[1].equalsIgnoreCase("legacy")) {
            // Defaults to plugins/PerPlayerKit/database.db
            java.io.File dbFile = new java.io.File(plugin.getDataFolder().getParentFile(), "PerPlayerKit/database.db");

            // If they passed a specific path like /perplayerkit import legacy myfile.db
            if (args.length >= 3) {
                dbFile = new java.io.File(plugin.getDataFolder(), args[2]);
            }

            new LegacyDatabaseImporter(dbFile, sender).importData();
            return true;
        }

        if (!args[1].equalsIgnoreCase("kitsx")) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "error.invalid-import-type");
            return true;
        }

        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "success.import-starting");
        KitsXImporter importer = new KitsXImporter(plugin, sender);
        if (!importer.checkForFiles()) {
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "error.import-files-missing");
            org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "info.import-instructions");
            return true;
        }

        importer.importFiles();
        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "success.import-attempted");
        return true;
    }


    private boolean handleMigrate(CommandSender sender, String[] args) {
        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "error.migration-not-supported");
        return true;
    }

    private void sendMigrateUsage(CommandSender sender) {
        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "command.perplayerkit-migrate-usage");
        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "info.available-storage-types");
    }

    private boolean validateStorageType(CommandSender sender, String storageType, String role) {
        if (STORAGE_TYPES.contains(storageType)) {
            return true;
        }

        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "error.invalid-storage-type", "role", role, "type", storageType);
        org.pvpcore.commands.kits.KitsCommands.lang().send(sender, "info.available-storage-types");
        return false;
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(org.pvpcore.commands.kits.KitsCommands.activeKits() != null && org.pvpcore.commands.kits.KitsCommands.activeKits().isEnabled())) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("about", "autosetup", "import", "migrate", "location");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("location")) {
            return Arrays.stream(LocationFeature.values()).map(LocationFeature::key).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return List.of("kitsx", "legacy"); // Added legacy here
        }

        // Only the console can reset, so only the console is offered it.
        if (args.length == 2 && args[0].equalsIgnoreCase("autosetup")) {
            return (sender instanceof ConsoleCommandSender) ? List.of("add-kits", "reset") : List.of("add-kits");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("autosetup") && args[1].equalsIgnoreCase("reset")) {
            return List.of("confirm");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
            return STORAGE_TYPES.stream()
                    .filter(type -> type.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("migrate")) {
            String sourceType = args[1].toLowerCase();
            return STORAGE_TYPES.stream()
                    .filter(type -> !type.equals(sourceType))
                    .filter(type -> type.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return null;
    }
}
