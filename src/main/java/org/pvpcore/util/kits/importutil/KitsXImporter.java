/*
 * Copyright 2022-2025 Noah Ross
 *
 * This file is part of Kits.
 * ...
 */
package org.pvpcore.util.kits.importutil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.data.KitRoomDataManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KitsXImporter {

    private final String kitroomFilePath = "data/kitroom.yml";
    private final String kitsFilePath = "data/kits.yml";
    private final String enderchestsFilePath = "data/enderchest.yml";

    private final Plugin plugin;
    private final CommandSender sender;

    public KitsXImporter(Plugin plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    public boolean checkForFiles() {
        File kitroom = new File(plugin.getDataFolder(), kitroomFilePath);
        File kits = new File(plugin.getDataFolder(), kitsFilePath);
        File enderchests = new File(plugin.getDataFolder(), enderchestsFilePath);
        return kitroom.exists() && kits.exists() && enderchests.exists();
    }

    public void importFiles() {
        if (!checkForFiles()) {
            sender.sendMessage(Component.text("Required files are missing. Cannot proceed with the import.", NamedTextColor.RED));
            return;
        }
        KitsContext kits = kitsContext();
        if (kits == null) {
            sender.sendMessage(Component.text("Kits module is not available; cannot import KitsX files.", NamedTextColor.RED));
            return;
        }
        importKitroom(sender, kits);
        importKits(sender, kits);
        importEnderchests(sender, kits);
    }

    private void importKitroom(CommandSender sender, KitsContext kits) {
        File kitroomFile = new File(plugin.getDataFolder(), kitroomFilePath);
        if (!kitroomFile.exists()) {
            sender.sendMessage(Component.text("kitroom.yml file not found! Skipping import.", NamedTextColor.RED));
            return;
        }
        KitRoomDataManager krd = kits.kitRoomDataManager();
        if (krd == null) {
            sender.sendMessage(Component.text("Kit room data manager is not available; skipping kitroom import.", NamedTextColor.RED));
            return;
        }
        YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(kitroomFile);
        if (yamlConfig.contains("categories")) {
            List<String> categoryKeys = new ArrayList<>(yamlConfig.getConfigurationSection("categories").getKeys(false));
            for (int categoryIndex = 0; categoryIndex < Math.min(krd.getPageCount(), categoryKeys.size()); categoryIndex++) {
                String category = categoryKeys.get(categoryIndex);
                sender.sendMessage(Component.text("Processing category: " + category, NamedTextColor.BLUE));
                ItemStack[] categoryItems = new ItemStack[45];
                int itemIndex = 0;
                for (String key : yamlConfig.getConfigurationSection("categories." + category).getKeys(false)) {
                    if (itemIndex >= 45) {
                        sender.sendMessage(Component.text("Ignoring additional items in category: " + category, NamedTextColor.YELLOW));
                        break;
                    }
                    try {
                        int slot = Integer.parseInt(key);
                        ItemStack itemStack = yamlConfig.getItemStack("categories." + category + "." + key);
                        if (itemStack != null && slot >= 0 && slot < 45) {
                            categoryItems[slot] = itemStack;
                            itemIndex++;
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("[Warning] Invalid slot number: " + key + " in category: " + category, NamedTextColor.GOLD));
                    }
                }
                krd.setKitRoom(categoryIndex, categoryItems);
                krd.savePagesToDBAsync(List.of(categoryIndex));
                sender.sendMessage(Component.text("Imported " + itemIndex + " items in category: " + category, NamedTextColor.GREEN));
            }
        }
        sender.sendMessage(Component.text("Finished importing kitroom.yml.", NamedTextColor.AQUA));
    }

    private void importKits(CommandSender sender, KitsContext kits) {
        File kitsFile = new File(plugin.getDataFolder(), kitsFilePath);
        if (!kitsFile.exists()) {
            sender.sendMessage(Component.text("kits.yml file not found! Skipping import.", NamedTextColor.RED));
            return;
        }
        KitManager km = kits.kitManager();
        if (km == null) {
            sender.sendMessage(Component.text("Kit manager is not available; skipping kits import.", NamedTextColor.RED));
            return;
        }
        YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(kitsFile);
        if (yamlConfig.getKeys(false).isEmpty()) {
            sender.sendMessage(Component.text("No player kits found in kits.yml.", NamedTextColor.YELLOW));
            return;
        }
        for (String uuid : yamlConfig.getKeys(false)) {
            sender.sendMessage(Component.text("Processing kits for player UUID: " + uuid, NamedTextColor.BLUE));
            if (!yamlConfig.isConfigurationSection(uuid)) {
                sender.sendMessage(Component.text("No kits found for UUID: " + uuid, NamedTextColor.YELLOW));
                continue;
            }
            for (String kitKey : yamlConfig.getConfigurationSection(uuid).getKeys(false)) {
                int kitNumber;
                try {
                    kitNumber = Integer.parseInt(kitKey.replace("Kit ", "").trim());
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("[Warning] Invalid kit number: " + kitKey + " for player UUID: " + uuid, NamedTextColor.GOLD));
                    continue;
                }
                sender.sendMessage(Component.text("Processing Kit " + kitNumber + " for player UUID: " + uuid, NamedTextColor.GREEN));
                if (!yamlConfig.isConfigurationSection(uuid + "." + kitKey)) {
                    sender.sendMessage(Component.text("No items found for Kit " + kitNumber + " for player UUID: " + uuid, NamedTextColor.YELLOW));
                    continue;
                }
                ItemStack[] kitItems = new ItemStack[41];
                for (String slotKey : yamlConfig.getConfigurationSection(uuid + "." + kitKey).getKeys(false)) {
                    int slot;
                    try {
                        slot = Integer.parseInt(slotKey);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("[Warning] Invalid slot number: " + slotKey + " in Kit " + kitNumber, NamedTextColor.GOLD));
                        continue;
                    }
                    if (slot < 0 || slot >= 41) {
                        sender.sendMessage(Component.text("Slot " + slot + " in Kit " + kitNumber + " is out of range (0-40). Skipping.", NamedTextColor.YELLOW));
                        continue;
                    }
                    ItemStack itemStack = yamlConfig.getItemStack(uuid + "." + kitKey + "." + slotKey);
                    if (itemStack != null) {
                        kitItems[slot] = itemStack;
                    }
                }
                km.savekit(UUID.fromString(uuid), kitNumber, kitItems);
            }
            km.savePlayerKitsToDB(UUID.fromString(uuid));
        }
        sender.sendMessage(Component.text("Finished importing kits.yml.", NamedTextColor.AQUA));
    }

    private void importEnderchests(CommandSender sender, KitsContext kits) {
        File kitsFile = new File(plugin.getDataFolder(), kitsFilePath);
        if (!kitsFile.exists()) {
            sender.sendMessage(Component.text("enderchest.yml file not found! Skipping import.", NamedTextColor.RED));
            return;
        }
        KitManager km = kits.kitManager();
        if (km == null) {
            sender.sendMessage(Component.text("Kit manager is not available; skipping enderchest import.", NamedTextColor.RED));
            return;
        }
        YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(kitsFile);
        if (yamlConfig.getKeys(false).isEmpty()) {
            sender.sendMessage(Component.text("No player kits found in kits.yml.", NamedTextColor.YELLOW));
            return;
        }
        for (String uuid : yamlConfig.getKeys(false)) {
            sender.sendMessage(Component.text("Processing EC for player UUID: " + uuid, NamedTextColor.BLUE));
            if (!yamlConfig.isConfigurationSection(uuid)) {
                sender.sendMessage(Component.text("No EC found for UUID: " + uuid, NamedTextColor.YELLOW));
                continue;
            }
            for (String kitKey : yamlConfig.getConfigurationSection(uuid).getKeys(false)) {
                int kitNumber;
                try {
                    kitNumber = Integer.parseInt(kitKey.replace("Kit ", "").trim());
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("[Warning] Invalid EC number: " + kitKey + " for player UUID: " + uuid, NamedTextColor.GOLD));
                    continue;
                }
                sender.sendMessage(Component.text("Processing EC " + kitNumber + " for player UUID: " + uuid, NamedTextColor.GREEN));
                if (!yamlConfig.isConfigurationSection(uuid + "." + kitKey)) {
                    sender.sendMessage(Component.text("No items found for EC " + kitNumber + " for player UUID: " + uuid, NamedTextColor.YELLOW));
                    continue;
                }
                ItemStack[] kitItems = new ItemStack[27];
                for (String slotKey : yamlConfig.getConfigurationSection(uuid + "." + kitKey).getKeys(false)) {
                    int slot;
                    try {
                        slot = Integer.parseInt(slotKey);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("[Warning] Invalid slot number: " + slotKey + " in EC " + kitNumber, NamedTextColor.GOLD));
                        continue;
                    }
                    if (slot < 0 || slot >= 27) {
                        sender.sendMessage(Component.text("Slot " + slot + " in EC " + kitNumber + " is out of range (0-26). Skipping.", NamedTextColor.YELLOW));
                        continue;
                    }
                    ItemStack itemStack = yamlConfig.getItemStack(uuid + "." + kitKey + "." + slotKey);
                    if (itemStack != null) {
                        kitItems[slot] = itemStack;
                    }
                }
                km.savekit(UUID.fromString(uuid), kitNumber, kitItems);
            }
            km.savePlayerKitsToDB(UUID.fromString(uuid));
        }
        sender.sendMessage(Component.text("Finished importing enderchest.yml.", NamedTextColor.AQUA));
    }

    private KitsContext kitsContext() {
        if (plugin instanceof PvPCorePlugin pvp) {
            return pvp.kitsContext();
        }
        try {
            PvPCorePlugin pvp = JavaPlugin.getPlugin(PvPCorePlugin.class);
            return pvp == null ? null : pvp.kitsContext();
        } catch (Throwable ignored) {
            return null;
        }
    }
}