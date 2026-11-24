/*
 * Copyright 2022-2025 Noah Ross
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
package org.pvpcore.features.kits.data;

import org.pvpcore.features.kits.KitContents;
import org.pvpcore.features.kits.ItemFilter;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitsContext;

import org.pvpcore.util.kits.IDUtil;
import org.pvpcore.util.kits.Serializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KitRoomDataManager {

    public static final int DEFAULT_PAGE_COUNT = 5; // why: public kit-room layout constant retained for external/legacy compatibility.
    public static final int MAX_PAGE_COUNT = 99; // why: public kit-room bounds constant retained for external/legacy compatibility.
    public static final int BUTTONS_PER_GROUP = 5; // why: public kit-room layout constant retained for external/legacy compatibility.
    @Deprecated public static final int PAGE_COUNT = DEFAULT_PAGE_COUNT; // why: legacy constant alias retained for external/legacy compatibility.

    private int pageCount;

    /** why: stateless config parsing helper retained for external/legacy compatibility and config validation. */
    public static int configuredPages(org.bukkit.configuration.ConfigurationSection config) {
        if (!config.contains("kitroom.pages")) return DEFAULT_PAGE_COUNT;
        if (!config.isInt("kitroom.pages") || config.getInt("kitroom.pages") < 1 || config.getInt("kitroom.pages") > MAX_PAGE_COUNT)
            throw new IllegalArgumentException("kitroom.pages must be a whole number from 1 to " + MAX_PAGE_COUNT);
        return config.getInt("kitroom.pages");
    }

    public int getPageCount() { return pageCount; }

    private final ArrayList<ItemStack[]> kitroomData;
    private final ArrayList<String> pageTitles = new ArrayList<>();
    private final ArrayList<String> pageNames = new ArrayList<>();
    private final ArrayList<Material> pageIcons = new ArrayList<>();
    private final Plugin plugin;
    private final org.pvpcore.features.kits.KitsContext context;
    private static KitRoomDataManager instance; // why: singleton instance pointer retained for legacy static accessor/lifecycle compatibility.
    private boolean loadedAnyPage;
    private final java.util.Set<Integer> storedPages = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final Object metaLock = new Object();
    private String pendingMeta;
    private final File metaFile;
    private final YamlConfiguration metaConfig;

    public KitRoomDataManager(KitsContext context) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.plugin = context.plugin();
        org.bukkit.configuration.file.FileConfiguration cfg = context.config();
        this.pageCount = configuredPages(cfg != null ? cfg : plugin.getConfig());
        kitroomData = new ArrayList<>();

        this.metaFile = new File(plugin.getDataFolder(), "kitroom_meta.yml");
        this.metaConfig = YamlConfiguration.loadConfiguration(metaFile);

        for (int i = 0; i < MAX_PAGE_COUNT; i++) {
            ItemStack[] defaultPage = new ItemStack[KitContents.ROOM_SIZE];
            kitroomData.add(defaultPage);
            pageTitles.add("Kit Room");
            pageNames.add("Page " + (i + 1));
            pageIcons.add(Material.CHEST);
        }

        loadMeta();
        if (itemFilter() != null) {
            itemFilter().addToWhitelist(kitroomData.subList(0, pageCount));
        }

        instance = this;
    }

    public KitsContext context() {
        return context;
    }

    private ItemFilter itemFilter() {
        return context.itemFilter();
    }

    private KitManager kitManager() {
        return context.kitManager();
    }

    private org.pvpcore.storage.kits.StorageManager storageManager() {
        return context.storageManager();
    }

    /** why: legacy singleton shim retained for external/legacy compatibility; no internal call sites. */
    @Deprecated
    public static KitRoomDataManager get() {
        if (instance == null) {
            throw new IllegalStateException("KitRoomDataManager has not been initialized yet!");
        }
        return instance;
    }

    private void loadMeta() {
        if (metaFile.exists()) {
            this.pageCount = Math.max(1, Math.min(MAX_PAGE_COUNT, metaConfig.getInt("page-count", pageCount)));
            for (int i = 0; i < pageCount; i++) {
                pageTitles.set(i, metaConfig.getString("pages." + i + ".title", "Kit Room"));
                pageNames.set(i, metaConfig.getString("pages." + i + ".name", "Page " + (i + 1)));
                String iconName = metaConfig.getString("pages." + i + ".icon", "CHEST");
                Material icon = Material.matchMaterial(iconName);
                pageIcons.set(i, icon != null ? icon : Material.CHEST);
            }
        } else {
            // AUTO IMPORT: If the meta file doesn't exist, grab the names/icons from the old config.yml
            org.bukkit.configuration.file.FileConfiguration cfg = context.config();
            org.bukkit.configuration.ConfigurationSection itemsSection = cfg == null ? null : cfg.getConfigurationSection("kitroom.items");
            if (itemsSection != null) {
                for (String key : itemsSection.getKeys(false)) {
                    try {
                        int pageIndex = Integer.parseInt(key) - 1;
                        if (pageIndex >= 0 && pageIndex < MAX_PAGE_COUNT) {
                            String name = itemsSection.getString(key + ".name", "Page " + (pageIndex + 1));
                            String title = itemsSection.getString(key + ".title", name);
                            String materialName = itemsSection.getString(key + ".material", "CHEST");
                            Material icon = Material.matchMaterial(materialName);

                            pageNames.set(pageIndex, name);
                            pageTitles.set(pageIndex, title);
                            if (icon != null) {
                                pageIcons.set(pageIndex, icon);
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }

                int configPages = cfg == null ? DEFAULT_PAGE_COUNT : cfg.getInt("kitroom.pages", DEFAULT_PAGE_COUNT);
                if (configPages > pageCount) {
                    this.pageCount = Math.min(configPages, MAX_PAGE_COUNT);
                }

                saveMeta(); // Save the imported data to the new kitroom_meta.yml
                plugin.getLogger().info("Auto-imported legacy kit room configurations into kitroom_meta.yml!");
            }
        }
    }

    /**
     * Writes kitroom_meta.yml. The YAML text is built on the calling thread (so it is a consistent
     * snapshot) and written off the main thread; a newer snapshot always replaces an unwritten one.
     */
    public void saveMeta() {
        metaConfig.set("page-count", pageCount);
        for (int i = 0; i < pageCount; i++) {
            metaConfig.set("pages." + i + ".title", pageTitles.get(i));
            metaConfig.set("pages." + i + ".name", pageNames.get(i));
            metaConfig.set("pages." + i + ".icon", pageIcons.get(i).name());
        }
        // Pages that no longer exist must not linger in the file.
        for (int i = pageCount; i < MAX_PAGE_COUNT; i++) {
            metaConfig.set("pages." + i, null);
        }
        synchronized (metaLock) {
            pendingMeta = metaConfig.saveToString();
        }
        if (plugin.isEnabled()) {
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushMeta);
        } else {
            flushMeta();
        }
    }

    private void flushMeta() {
        String text;
        synchronized (metaLock) {
            text = pendingMeta;
            pendingMeta = null;
        }
        if (text == null) return;
        try {
            java.nio.file.Files.writeString(metaFile.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save kitroom_meta.yml: " + e.getMessage());
        }
    }

    public void setKitRoom(int page, ItemStack[] data) {
        if (page < 0 || page >= MAX_PAGE_COUNT || !KitContents.hasSize(data, KitContents.ROOM_SIZE))
            throw new IllegalArgumentException("Expected a kit room page from 0 to 98 with 45 slots");
        kitroomData.set(page, KitContents.copy(data));

        itemFilter().clearWhitelist();
        itemFilter().addToWhitelist(kitroomData.subList(0, pageCount));
    }

    public ItemStack[] getKitRoomPage(int page) {
        return KitContents.copy(kitroomData.get(page));
    }

    public void saveToDBAsync() {
        List<Integer> allPages = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            allPages.add(i);
        }
        savePagesToDBAsync(allPages);
    }

    public void savePagesToDBAsync(List<Integer> pages) {
        List<Integer> targets = List.copyOf(pages);
        if (targets.stream().anyMatch(page -> page < 0 || page >= MAX_PAGE_COUNT))
            throw new IllegalArgumentException("Invalid kit room page");
        storedPages.addAll(targets);
        if (targets.stream().anyMatch(page -> page < pageCount)) {
            loadedAnyPage = true;
        }
        for (int page : targets) {
            String output = Serializer.itemStackArrayToBase64(KitContents.copy(kitroomData.get(page)));
            kitManager().queueWrite(IDUtil.getKitRoomId(page), output);
        }
    }

    /** why: stateless page-range helper retained for external/legacy compatibility. */
    public static boolean hasStoredPage(int page) {
        return get().storedPages.contains(page);
    }

    public boolean loadedAnyPage() {
        return loadedAnyPage;
    }

    public String getPageTitle(int page) { return page < pageTitles.size() ? pageTitles.get(page) : "Kit Room"; }
    public String getPageName(int page) { return page < pageNames.size() ? pageNames.get(page) : ("Page " + (page + 1)); }
    public Material getPageIcon(int page) { return page < pageIcons.size() ? pageIcons.get(page) : Material.CHEST; }

    public void setPageSetting(int page, boolean isTitle, String value) {
        if (page < 0 || page >= MAX_PAGE_COUNT) return;
        if (isTitle) { if (page < pageTitles.size()) pageTitles.set(page, value); }
        else { if (page < pageNames.size()) pageNames.set(page, value); }
        saveMeta();
    }

    public void setPageIcon(int page, Material m) {
        if (page >= 0 && page < pageIcons.size()) pageIcons.set(page, m);
        saveMeta();
    }

    public void addPage() {
        if (pageCount >= MAX_PAGE_COUNT) return;
        pageCount++;
        saveMeta();
        itemFilter().clearWhitelist();
        itemFilter().addToWhitelist(kitroomData.subList(0, pageCount));
    }

    public void removePage(int page) {
        if (page < 0 || page >= pageCount || pageCount <= 1) return; // Cannot delete the last remaining page

        // Shift data left
        for (int i = page; i < pageCount - 1; i++) {
            kitroomData.set(i, kitroomData.get(i + 1));
            pageTitles.set(i, pageTitles.get(i + 1));
            pageNames.set(i, pageNames.get(i + 1));
            pageIcons.set(i, pageIcons.get(i + 1));
        }

        // Clear the last page
        kitroomData.set(pageCount - 1, new ItemStack[KitContents.ROOM_SIZE]);
        pageTitles.set(pageCount - 1, "Kit Room");
        pageNames.set(pageCount - 1, "Page " + pageCount);
        pageIcons.set(pageCount - 1, Material.CHEST);

        pageCount--;
        // The page that fell off the end still has a row in the database. Delete it, otherwise its old
        // items would come back the next time a page is added or the server restarts.
        kitManager().queueWrite(IDUtil.getKitRoomId(pageCount), "!delete");
        storedPages.remove(pageCount);
        saveMeta();

        // Save all pages to overwrite the shifted data in the DB
        saveToDBAsync();

        itemFilter().clearWhitelist();
        itemFilter().addToWhitelist(kitroomData.subList(0, pageCount));
    }

    public void loadFromDB() {
        itemFilter().clearWhitelist();
        loadedAnyPage = false;
        storedPages.clear();
        for (int i = 0; i < pageCount; i++) {
            String input = storageManager().getKitDataByID(IDUtil.getKitRoomId(i));

            // Safely ignore empty, error, or tombstoned pages
            if (input == null || input.equalsIgnoreCase("error") || input.equals("!delete")) {
                continue;
            }

            loadedAnyPage = true;
            storedPages.add(i);

            try {
                ItemStack[] pagedata = Serializer.itemStackArrayFromBase64(input);

                // Ignore legacy-deleted pages
                if (pagedata.length == 0) {
                    continue;
                }

                if (!KitContents.hasSize(pagedata, KitContents.ROOM_SIZE)) {
                    throw new IOException("Expected " + KitContents.ROOM_SIZE + " kit room slots");
                }
                kitroomData.set(i, pagedata);
            } catch (Exception e) {
                plugin.getLogger().warning("Cannot load kit room page " + (i + 1) + ": " + e.getMessage());
            }
        }
        itemFilter().addToWhitelist(kitroomData.subList(0, pageCount));
    }

    /** Rebuild the ItemFilter material whitelist from the current kit-room pages. */
    public void refreshWhitelist() {
        itemFilter().clearWhitelist();
        itemFilter().addToWhitelist(kitroomData.subList(0, pageCount));
    }

    /** why: static shutdown retained for module disable/reload lifecycle cleanup of singleton-style state. */
    public static void shutdown() {
        if (instance != null) instance.flushMeta();
        instance = null;
    }
}