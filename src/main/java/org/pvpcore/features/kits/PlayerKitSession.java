package org.pvpcore.features.kits;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.util.kits.IDUtil;
import org.pvpcore.util.kits.Lang;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;

/**
 * Per-player session/load state and async orchestration. Package-private helper owned by {@link KitManager};
 * not exposed through {@link KitsContext}.
 */
final class PlayerKitSession {
    private final KitsContext context;
    private final KitCache cache;
    private final KitRecordStore store;
    private final IntSupplier maxConfiguredSlots;
    private final BiFunction<Player, Integer, Boolean> loadEnderchestSilent;

    private final Map<UUID, KitManager.KitReference> lastKitUsedByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastEnderchest = new ConcurrentHashMap<>();
    private final Map<UUID, Object> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    private record LoadedPlayerData(Map<String, String> kits, Map<String, Long> generations,
                                    String inventorySelection, String enderchestSelection,
                                    long inventorySelectionGeneration, long enderchestSelectionGeneration) {}

    PlayerKitSession(KitsContext context, KitCache cache, KitRecordStore store,
                     IntSupplier maxConfiguredSlots, BiFunction<Player, Integer, Boolean> loadEnderchestSilent) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.cache = java.util.Objects.requireNonNull(cache, "cache");
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.maxConfiguredSlots = java.util.Objects.requireNonNull(maxConfiguredSlots, "maxConfiguredSlots");
        this.loadEnderchestSilent = java.util.Objects.requireNonNull(loadEnderchestSilent, "loadEnderchestSilent");
    }

    private Lang lang() {
        return context.lang();
    }

    int getLastKitLoaded(UUID uuid) {
        KitManager.KitReference ref = lastKitUsedByPlayer.get(uuid);
        return ref == null || ref.slot() == null ? -1 : ref.slot();
    }

    KitManager.KitReference getLastKitReference(UUID uuid) {
        return lastKitUsedByPlayer.get(uuid);
    }

    boolean hasLastKit(UUID uuid) {
        KitManager.KitReference ref = lastKitUsedByPlayer.get(uuid);
        return ref != null && cache.contains(ref.key(uuid));
    }

    boolean isLoading(UUID uuid) {
        return loading.contains(uuid);
    }

    void rememberKit(UUID uuid, KitManager.KitReference reference) {
        lastKitUsedByPlayer.put(uuid, reference);
        store.queueWrite(KitSelection.kitKey(uuid), KitSelection.encode(reference));
    }

    void rememberEnderchest(UUID uuid, int slot) {
        lastEnderchest.put(uuid, slot);
        store.queueWrite(KitSelection.enderchestKey(uuid), Integer.toString(slot));
    }

    void restoreLastEnderchest(Player player) {
        Integer slot = lastEnderchest.get(player.getUniqueId());
        if (slot != null) loadEnderchestSilent.apply(player, slot);
    }

    void loadPlayerDataAsync(UUID uuid) {
        Object session = new Object();
        sessions.put(uuid, session);
        loading.add(uuid);
        final int max = maxConfiguredSlots.getAsInt();
        store.storageWork().supply(() -> {
            Map<String, String> data = new LinkedHashMap<>();
            Map<String, Long> generations = new LinkedHashMap<>();
            for (int slot = 1; slot <= max; slot++) {
                for (String id : List.of(IDUtil.getPlayerKitId(uuid, slot), IDUtil.getECId(uuid, slot))) {
                    // Capture BEFORE reading: a write between read and capture would make
                    // stale data look current.
                    long generation = store.generation(id);
                    data.put(id, store.readStoredData(id));
                    generations.put(id, generation);
                }
            }
            String inventoryKey = KitSelection.kitKey(uuid);
            long inventorySelectionGeneration = store.generation(inventoryKey);
            String inventorySelection = store.readStoredData(inventoryKey);
            String enderchestKey = KitSelection.enderchestKey(uuid);
            long enderchestSelectionGeneration = store.generation(enderchestKey);
            String enderchestSelection = store.readStoredData(enderchestKey);
            return new LoadedPlayerData(data, generations, inventorySelection, enderchestSelection,
                    inventorySelectionGeneration, enderchestSelectionGeneration);
        }).whenComplete((data, error) -> {
            if (!context.plugin().isEnabled()) {
                loading.remove(uuid);
                return;
            }
            context.plugin().getServer().getScheduler().runTask(context.plugin(), () -> {
                if (sessions.get(uuid) != session) return;
                if (error != null) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) lang().send(player, "error.kit-data-unavailable");
                    loading.remove(uuid);
                    return;
                }
                Map<String, ItemStack[]> parsed = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : data.kits().entrySet()) {
                    // A write submitted after this snapshot was taken wins: never let a stale
                    // read overwrite the newer cached kit.
                    if (!store.isCurrent(entry.getKey(), data.generations().getOrDefault(entry.getKey(), -1L))) {
                        continue;
                    }
                    String encoded = entry.getValue();
                    if (!KitRecordStore.isStoredData(encoded)) continue;
                    int size = entry.getKey().startsWith(uuid + "ec") ? KitContents.ENDERCHEST_SIZE : KitContents.INVENTORY_SIZE;
                    try {
                        ItemStack[] decoded = KitRecordStore.decodeKit(encoded, size);
                        if (decoded != null) {
                            parsed.put(entry.getKey(), decoded);
                        }
                    } catch (IOException e) {
                        context.plugin().getLogger().warning("Cannot load kit " + entry.getKey() + ": " + e.getMessage());
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) lang().send(player, "error.kit-data-unavailable");
                        continue;
                    }
                }
                parsed.forEach(cache::cacheKit); // Force update cache
                if (store.isCurrent(KitSelection.kitKey(uuid), data.inventorySelectionGeneration())
                        && store.isCurrent(KitSelection.enderchestKey(uuid), data.enderchestSelectionGeneration())) {
                    restoreSelections(uuid, data.inventorySelection(), data.enderchestSelection());
                }
                loading.remove(uuid);
            });
        });
    }

    void restoreSelections(UUID uuid, String inventory, String enderchest) {
        // A corrupt preference must not prevent access to otherwise valid saved kits.
        if (KitRecordStore.isStoredData(inventory)) {
            try { lastKitUsedByPlayer.putIfAbsent(uuid, KitSelection.decodeKit(inventory)); }
            catch (IllegalArgumentException error) { context.plugin().getLogger().warning("Cannot restore remembered kit for " + uuid + ": " + error.getMessage()); }
        }
        if (KitRecordStore.isStoredData(enderchest)) {
            try { lastEnderchest.putIfAbsent(uuid, KitSelection.decodeSlot(enderchest)); }
            catch (IllegalArgumentException error) { context.plugin().getLogger().warning("Cannot restore remembered enderchest for " + uuid + ": " + error.getMessage()); }
        }
    }

    void unloadPlayer(UUID uuid) {
        sessions.remove(uuid);
        loading.remove(uuid);
        lastKitUsedByPlayer.remove(uuid);
        lastEnderchest.remove(uuid);
        // Contents and selections are already queued. Quit only clears this session's cache;
        // it must not overwrite a selection saved by another server using the same database.
        // FIXED: Iterate to absolute max to clear any stale higher slots
        cache.removePlayerSlots(uuid, 99);
    }
}
