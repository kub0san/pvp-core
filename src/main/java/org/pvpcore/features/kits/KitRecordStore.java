package org.pvpcore.features.kits;

import org.pvpcore.config.ConfigFiles;
import org.pvpcore.storage.StorageWorkQueue;
import org.pvpcore.storage.kits.StorageManager;
import org.pvpcore.util.kits.IDUtil;
import org.pvpcore.util.kits.Serializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Persistence mechanics for kits: DB encode/decode, storage work queue, failed writes and retry.
 * Package-private helper owned by {@link KitManager}; not exposed through {@link KitsContext}.
 */
final class KitRecordStore {
    static final String DELETED = "!delete"; // why: internal storage sentinel constant retained as shared static marker.

    private final KitsContext context;
    private final KitCache cache;
    private final StorageWorkQueue storageWork;
    private final Logger logger;
    private final Map<String, String> failedWrites = new ConcurrentHashMap<>();

    KitRecordStore(KitsContext context, KitCache cache) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.cache = java.util.Objects.requireNonNull(cache, "cache");
        StorageWorkQueue queue = context.storageWork();
        this.logger = context.plugin().getLogger();
        this.storageWork = queue != null ? queue : new StorageWorkQueue(logger);
        if (queue == null) {
            context.setStorageWork(this.storageWork);
        }
    }

    private StorageManager storageManager() {
        StorageManager storageManager = context.storageManager();
        if (storageManager == null) {
            throw new IllegalStateException("Storage manager is not initialized");
        }
        return storageManager;
    }

    StorageWorkQueue storageWork() {
        return storageWork;
    }

    String deletedMarker() {
        return DELETED;
    }

    static boolean isStoredData(String data) {
        return data != null && !DELETED.equals(data);
    }

    static ItemStack[] decodeKit(String data, int size) throws IOException {
        ItemStack[] kit = Serializer.itemStackArrayFromBase64(data);
        // If length is 0, it was a legacy "deleted" string. Skip it silently.
        if (kit.length == 0) {
            return null;
        }
        if (!KitContents.hasSize(kit, size)) throw new IOException("Expected " + size + " inventory slots");
        return kit;
    }

    String readStoredData(String id) {
        String pending = failedWrites.get(id);
        return pending != null ? pending : storageManager().getKitDataByID(id);
    }

    void writeStoredData(String key, String data) {
        if (DELETED.equals(data)) storageManager().deleteKitByID(key);
        else storageManager().saveKitDataByID(key, data);
    }

    void queueWrite(String key, String data) {
        storageWork.run(() -> {
            try {
                writeStoredData(key, data);
                failedWrites.remove(key);
            } catch (RuntimeException e) {
                failedWrites.put(key, data);
                throw e;
            }
        });
    }

    void retryFailedWrites() {
        failedWrites.forEach((key, data) -> storageWork.run(() -> {
            if (!data.equals(failedWrites.get(key))) return;
            writeStoredData(key, data);
            failedWrites.remove(key, data);
        }));
    }

    void saveKitToDB(String key) {
        ItemStack[] snapshot = KitContents.copy(cache.getRaw(key));
        if (snapshot == null) return;
        String data = Serializer.itemStackArrayToBase64(snapshot);
        queueWrite(key, data);
    }

    void savePlayerKitToDB(UUID uuid, int slot) {
        saveKitToDB(IDUtil.getPlayerKitId(uuid, slot));
    }

    void saveEnderchestToDB(UUID uuid, int slot) {
        saveKitToDB(IDUtil.getECId(uuid, slot));
    }

    void savePublicKitToDB(String id) {
        saveKitToDB(IDUtil.getPublicKitId(id));
    }

    void savePlayerKitsToDB(UUID uuid, int max) {
        for (int i = 1; i <= max; i++) {
            savePlayerKitToDB(uuid, i);
            saveEnderchestToDB(uuid, i);
        }
    }

    void loadKitEntryFromDB(String id, int size) {
        String data = storageManager().getKitDataByID(id);
        if (!isStoredData(data)) return;
        try {
            ItemStack[] decoded = decodeKit(data, size);
            if (decoded != null) {
                cache.cacheKit(id, decoded);
            }
        } catch (IOException e) {
            logger.warning("Cannot load kit " + id + ": " + e.getMessage());
        }
    }

    void loadPlayerKitFromDB(UUID uuid, int slot) {
        loadKitEntryFromDB(IDUtil.getPlayerKitId(uuid, slot), KitContents.INVENTORY_SIZE);
    }

    void loadPlayerEnderchestFromDB(UUID uuid, int slot) {
        loadKitEntryFromDB(IDUtil.getECId(uuid, slot), KitContents.ENDERCHEST_SIZE);
    }

    void loadPlayerDataFromDB(UUID uuid, int max, java.util.function.BiConsumer<String, String> restoreSelections) {
        for (int slot = 1; slot <= max; slot++) {
            loadPlayerKitFromDB(uuid, slot);
        }
        for (int slot = 1; slot <= max; slot++) {
            loadPlayerEnderchestFromDB(uuid, slot);
        }
        restoreSelections.accept(readStoredData(KitSelection.kitKey(uuid)), readStoredData(KitSelection.enderchestKey(uuid)));
    }

    void loadPublicKitFromDB(String id) {
        loadKitEntryFromDB(IDUtil.getPublicKitId(id), KitContents.INVENTORY_SIZE);
    }

    boolean deleteCachedKit(String id) {
        if (!cache.contains(id)) return false;
        cache.remove(id);
        queueWrite(id, DELETED);
        return true;
    }

    boolean deleteKit(UUID uuid, int slot) {
        return deleteCachedKit(IDUtil.getPlayerKitId(uuid, slot));
    }

    boolean deleteEnderchest(UUID uuid, int slot) {
        return deleteCachedKit(IDUtil.getECId(uuid, slot));
    }

    void deletePublicKit(String id) {
        String key = IDUtil.getPublicKitId(id);
        cache.remove(key);
        queueWrite(key, DELETED);
    }

    void shutdown(FileConfiguration config) {
        storageWork.close();
        if (!failedWrites.isEmpty()) {
            YamlConfiguration recovery = new YamlConfiguration();
            recovery.set("storage-type", config == null ? null : config.getString("storage.type"));
            failedWrites.forEach((id, data) -> recovery.set((DELETED.equals(data) ? "delete." : "kits.") + id, DELETED.equals(data) ? true : data));
            try {
                Path file = context.plugin().getDataFolder().toPath().resolve("failed-kit-writes-" + System.currentTimeMillis() + ".yml");
                ConfigFiles.write(recovery, file);
                logger.severe("Failed kit saves retained in " + file.getFileName() + ". Restore them before using a different backend.");
            } catch (IOException e) {
                logger.severe("Cannot write kit recovery file: " + e.getMessage());
            }
        }
    }
}
