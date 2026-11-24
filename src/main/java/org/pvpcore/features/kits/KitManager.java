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

package org.pvpcore.features.kits;

import org.pvpcore.util.kits.BroadcastManager;
import org.pvpcore.util.kits.IDUtil;
import org.pvpcore.util.kits.KitSlots;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.Serializer;
import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.pvpcore.util.kits.SoundManager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.bukkit.plugin.Plugin;
import org.pvpcore.storage.StorageWorkQueue;

public class KitManager {
    private static volatile KitManager instance; // why: singleton instance pointer retained for legacy static accessor compatibility.
    private final KitsContext context;
    private final KitCache cache = new KitCache();
    private final KitRecordStore store;
    private final PlayerKitSession sessions;
    private final KitLoader loader;
    public record KitReference(Integer slot, String publicId) {
        String key(UUID player) { return publicId == null ? IDUtil.getPlayerKitId(player, slot) : IDUtil.getPublicKitId(publicId); }
    }

    public KitManager(KitsContext context) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.store = new KitRecordStore(context, cache);
        this.sessions = new PlayerKitSession(context, cache, store, this::maxConfiguredSlots, (p, s) -> loadEnderchestSilent(p, s));
        this.loader = new KitLoader(context, cache, sessions);
        instance = this;
    }

    public KitsContext context() {
        return context;
    }

    /**
     * Delivers save feedback only once the storage write has resolved. The storage worker is
     * off the main thread, so the callback is marshalled back before touching Bukkit state,
     * and the player is re-resolved in case they left while the write was queued.
     */
    static void reportWhenWritten(Plugin plugin, UUID playerId, CompletableFuture<Void> write,
                                  BiConsumer<Player, Boolean> reporter) {
        write.whenComplete((unused, error) -> {
            if (!plugin.isEnabled()) return; // shutdown: never schedule onto a disabled plugin
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        reporter.accept(player, error == null);
                    }
                });
            } catch (org.bukkit.plugin.IllegalPluginAccessException disabled) {
                // The plugin disabled between the check and scheduling; feedback is moot.
            }
        });
    }

    private org.bukkit.configuration.file.FileConfiguration config() {
        return context.config();
    }

    private org.pvpcore.storage.kits.StorageManager storageManager() {
        return context.storageManager();
    }

    private Lang lang() {
        return context.lang();
    }

    private ItemFilter itemFilter() {
        return context.itemFilter();
    }

    private BroadcastManager broadcastManager() {
        return context.broadcastManager();
    }

    /** why: legacy singleton shim retained for external/legacy compatibility; no internal call sites. */
    @Deprecated
    public static KitManager get() {
        KitManager current = instance;
        if (current == null) {
            throw new IllegalStateException("KitManager not initialized");
        }
        return current;
    }

    public ItemStack[] getItemStackArrayById(String id) {
        return cache.getItemStackArrayById(id);
    }

    /** Takes ownership of a prepared or decoded snapshot; outward reads always copy it. */

    private void cacheKit(String id, ItemStack[] kit) {
        cache.cacheKit(id, kit);
    }

    public List<PublicKit> getPublicKitList() {
        return cache.getPublicKitList();
    }

    /**
     * Refreshes a cached kit after its stored data was rewritten externally
     * (e.g. by an admin item purge). Passing null removes the cached entry;
     * a non-null kit only replaces an existing entry, so kits of offline
     * players are not pulled into the cache.
     */

    public void updateCachedKit(String id, ItemStack[] kit) {
        cache.updateCachedKit(id, kit);
    }

    /** Legacy accessor: returns -1 when the last kit is public. */

    public int getLastKitLoaded(UUID uuid) {
        return sessions.getLastKitLoaded(uuid);
    }

    public KitReference getLastKitReference(UUID uuid) { return sessions.getLastKitReference(uuid); }

    public boolean hasLastKit(UUID uuid) {
        return sessions.hasLastKit(uuid);
    }

    public boolean isLoading(UUID uuid) { return sessions.isLoading(uuid); }

    public StorageWorkQueue storageWork() { return store.storageWork(); }

    /** Replays failed-write recovery files before any cache load is queued. */
    void replayFailedWrites() {
        store.replayRecoveryFiles();
    }

    public boolean savekit(UUID uuid, int slot, ItemStack[] kit) {
        return savePersonalKit(uuid, slot, kit, false, false);
    }

    public boolean savekit(UUID uuid, int slot, ItemStack[] kit, boolean silent) {
        return savePersonalKit(uuid, slot, kit, false, silent);
    }

    public boolean saveEC(UUID uuid, int slot, ItemStack[] kit) {
        return savePersonalKit(uuid, slot, kit, true, false);
    }

    public boolean saveECSilent(UUID uuid, int slot, ItemStack[] kit) {
        return savePersonalKit(uuid, slot, kit, true, true);
    }

    private boolean savePersonalKit(UUID uuid, int slot, ItemStack[] contents, boolean enderchest, boolean silent) {
        if (uuid == null || slot < 1 || slot > 99 || sessions.isLoading(uuid)) return false; // why: absolute slot bounds constant values retained by domain rules

        Player player = silent ? null : Bukkit.getPlayer(uuid);
        if (!silent && player == null) return false;

        if (itemFilter().filtersSaves()) {
            if (!itemFilter().isReady()) {
                if (player != null) lang().send(player, "error.kitroom-not-ready");
                return false;
            }
            contents = itemFilter().filterKit(contents, player);
            if (contents == null) return false;
        }
        ItemStack[] prepared = KitContents.prepare(contents, enderchest);
        if (prepared == null) {
            if (player != null) lang().send(player, enderchest ? "error.empty-ec" : "error.empty-kit");
            return false;
        }
        String id = enderchest ? IDUtil.getECId(uuid, slot) : IDUtil.getPlayerKitId(uuid, slot);
        cacheKit(id, prepared);
        if (silent) {
            // Editor autosaves, kit swaps and share deliveries must not spam chat.
            store.saveKitToDB(id);
            return true;
        }
        UUID playerId = uuid;
        boolean enderchestSlot = enderchest;
        int savedSlot = slot;
        reportWhenWritten(context.plugin(), playerId, store.saveKitToDB(id), (recipient, ok) -> {
            if (ok) {
                lang().send(recipient, enderchestSlot ? "success.ec-saved" : "success.kit-saved",
                        "slot", String.valueOf(savedSlot));
            } else {
                lang().send(recipient, "error.public-kit-save-failed", "kitid", id);
            }
        });
        return true;
    }

    public boolean savePublicKit(Player player, String id, ItemStack[] contents) {
        if (player == null) return false;
        if (!savePublicKit(id, contents)) {
            lang().send(player, "error.empty-kit");
            return false;
        }
        String savedId = id;
        reportWhenWritten(context.plugin(), player.getUniqueId(), store.savePublicKitToDB(id), (recipient, ok) -> {
            if (ok) {
                lang().send(recipient, "success.public-kit-saved", "kitname", savedId);
            } else {
                lang().send(recipient, "error.public-kit-save-failed", "kitid", savedId);
            }
        });
        return true;
    }

    /** Caches a public kit. Call savePublicKitToDB after preparing a batch of public kits. */

    public boolean savePublicKit(String id, ItemStack[] contents) {
        if (id == null || id.isBlank()) return false;
        ItemStack[] prepared = KitContents.prepare(contents, false);
        if (prepared == null) return false;
        cacheKit(IDUtil.getPublicKitId(id), prepared);
        return true;
    }

    public boolean regearKit(Player player, int slot) {
        return KitItemSupport.regearKit(player, slot, cache, sessions, loader, itemFilter(), config());
    }

    public boolean regearLastKit(Player player) {
        return KitItemSupport.regearLastKit(player, cache, sessions, loader, itemFilter(), config());
    }

    public boolean loadKit(Player player, int slot) {
        return loader.loadKit(player, slot);
    }

    public boolean loadKitSilent(Player player, int slot) {
        return loader.loadKitSilent(player, slot);
    }

    public boolean loadPublicKit(Player player, String id) {
        return loader.loadPublicKit(player, id);
    }

    public boolean loadPublicKitSilent(Player player, String id) {
        return loader.loadPublicKitSilent(player, id);
    }

    public boolean loadEnderchest(Player player, int slot) {
        return loader.loadEnderchest(player, slot);
    }

    public boolean loadEnderchestSilent(Player player, int slot) {
        return loader.loadEnderchestSilent(player, slot);
    }

    public boolean loadLastKit(Player player) {
        return loader.loadLastKit(player);
    }

    public void restoreLastEnderchest(Player player) {
        sessions.restoreLastEnderchest(player);
    }

    public boolean hasKit(UUID uuid, int slot) {
        return cache.contains(IDUtil.getPlayerKitId(uuid, slot));
    }

    public boolean hasEC(UUID uuid, int slot) {
        return cache.contains(IDUtil.getECId(uuid, slot));
    }

    public ItemStack[] getPlayerEC(UUID uuid, int slot) {
        return KitContents.copy(cache.getRaw(IDUtil.getECId(uuid, slot)));
    }

    public ItemStack[] getPlayerKit(UUID uuid, int slot) {
        return KitContents.copy(cache.getRaw(IDUtil.getPlayerKitId(uuid, slot)));
    }

    public boolean hasPublicKit(String id) {
        return cache.hasPublicKit(id);
    }

    public ItemStack[] getPublicKit(String id) {
        return cache.getPublicKit(id);
    }

    public void loadPlayerDataFromDB(UUID uuid) {
        store.loadPlayerDataFromDB(uuid, maxConfiguredSlots(), (inv, ec) -> restoreSelections(uuid, inv, ec));
    }

    public void loadPlayerKitFromDB(UUID uuid, int slot) {
        store.loadPlayerKitFromDB(uuid, slot);
    }

    public void loadPlayerEnderchestFromDB(UUID uuid, int slot) {
        store.loadPlayerEnderchestFromDB(uuid, slot);
    }

    public void savePlayerKitsToDB(UUID uuid) {
        store.savePlayerKitsToDB(uuid, maxConfiguredSlots());
    }

    public void savePlayerKitToDB(UUID uuid, int slot) { store.savePlayerKitToDB(uuid, slot); }
    public void saveEnderchestToDB(UUID uuid, int slot) { store.saveEnderchestToDB(uuid, slot); }
    public void savePublicKitToDB(String id) { store.savePublicKitToDB(id); }

    public void queueWrite(String key, String data) {
        store.queueWrite(key, data);
    }

    public void retryFailedWrites() {
        store.retryFailedWrites();
    }

    public void loadPlayerDataAsync(UUID uuid) {
        sessions.loadPlayerDataAsync(uuid);
    }

    private void restoreSelections(UUID uuid, String inventory, String enderchest) {
        sessions.restoreSelections(uuid, inventory, enderchest);
    }

    public void unloadPlayer(UUID uuid) {
        sessions.unloadPlayer(uuid);
    }

    private int maxConfiguredSlots() {
        org.bukkit.configuration.file.FileConfiguration cfg = config();
        int configured = cfg == null ? 9 : cfg.getInt("kits.max-slots", 9);
        return Math.max(1, Math.min(99, configured));
    }

    public void shutdown() {
        store.shutdown(config());
        instance = null;
    }

    public void loadPublicKitFromDB(String id) {
        store.loadPublicKitFromDB(id);
    }

    public void deletePublicKit(String id) {
        store.deletePublicKit(id);
    }

    public boolean deleteKit(UUID uuid, int slot) {
        return store.deleteKit(uuid, slot);
    }

    public boolean deleteEnderchest(UUID uuid, int slot) {
        return store.deleteEnderchest(uuid, slot);
    }

    // ------------------------------------------------------------------ kind-based API

    /** Whether the player has something saved in that slot. */
    public boolean has(KitKind kind, UUID uuid, int slot) {
        return cache.contains(kind.id(uuid, slot));
    }

    /** An owned copy of the slot's contents, or null. Safe to modify. */
    public ItemStack[] get(KitKind kind, UUID uuid, int slot) {
        return KitContents.copy(cache.getRaw(kind.id(uuid, slot)));
    }

    /**
     * The cached contents without copying, or null. For rendering only (GUI cards, previews):
     * it avoids cloning up to 41 stacks per slot per redraw. Callers must never modify it.
     */
    public ItemStack[] peek(KitKind kind, UUID uuid, int slot) {
        return cache.getRaw(kind.id(uuid, slot));
    }

    /** Read-only view of a premade kit, see {@link #peek}. */
    public ItemStack[] peekPublicKit(String id) {
        return cache.peekPublicKit(id);
    }

    public boolean load(KitKind kind, Player player, int slot) {
        return kind == KitKind.KIT ? loadKit(player, slot) : loadEnderchest(player, slot);
    }

    public boolean delete(KitKind kind, UUID uuid, int slot) {
        return kind == KitKind.KIT ? deleteKit(uuid, slot) : deleteEnderchest(uuid, slot);
    }

    // ------------------------------------------------------------------ premade kit registry

    public PublicKit findPublicKit(String id) {
        return cache.findPublicKit(id);
    }

    public void registerPublicKit(PublicKit kit) {
        cache.registerPublicKit(kit);
    }

    public boolean removePublicKit(PublicKit kit) {
        return cache.removePublicKit(kit);
    }
}

