package org.pvpcore.features.kits;

import org.pvpcore.util.kits.IDUtil;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory kit cache and public-kit registry. Package-private helper owned by {@link KitManager};
 * not exposed through {@link KitsContext}.
 */
final class KitCache {
    private final Map<String, ItemStack[]> kits = new ConcurrentHashMap<>();
    // Exposed by getPublicKitList() and iterated from async snapshot reads while the main
    // thread registers/removes public kits; a plain ArrayList throws ConcurrentModificationException.
    private final List<PublicKit> publicKits = new CopyOnWriteArrayList<>();

    ItemStack[] getRaw(String id) {
        return kits.get(id);
    }

    boolean contains(String id) {
        return kits.containsKey(id);
    }

    /**
     * Takes ownership of a prepared or decoded snapshot; outward reads always copy it.
     */
    void cacheKit(String id, ItemStack[] kit) {
        if (kit == null) {
            kits.remove(id);
            return;
        }
        kits.put(id, kit);
    }

    /**
     * Refreshes a cached kit after its stored data was rewritten externally (e.g. by an admin item purge).
     * Passing null removes the cached entry; a non-null kit only replaces an existing entry.
     */
    void updateCachedKit(String id, ItemStack[] kit) {
        if (kit == null) {
            kits.remove(id);
        } else {
            kits.computeIfPresent(id, (key, oldKit) -> KitContents.copy(kit));
        }
    }

    void remove(String id) {
        kits.remove(id);
    }

    void removePlayerSlots(UUID uuid, int absoluteMaxSlot) {
        for (int slot = 1; slot <= absoluteMaxSlot; slot++) {
            kits.remove(IDUtil.getPlayerKitId(uuid, slot));
            kits.remove(IDUtil.getECId(uuid, slot));
        }
    }

    List<PublicKit> getPublicKitList() {
        return publicKits;
    }

    PublicKit findPublicKit(String id) {
        for (PublicKit kit : publicKits) {
            if (kit.id.equals(id)) return kit;
        }
        return null;
    }

    void registerPublicKit(PublicKit kit) {
        publicKits.add(kit);
    }

    boolean removePublicKit(PublicKit kit) {
        return publicKits.remove(kit);
    }

    /**
     * Clears the public-kit registry and any cached public-kit contents. Used on reload
     * so kits removed from config do not linger in the contents map.
     */
    void clearPublicKits() {
        for (PublicKit kit : publicKits) {
            kits.remove(IDUtil.getPublicKitId(kit.id));
        }
        publicKits.clear();
    }

    boolean hasPublicKit(String id) {
        return kits.containsKey(IDUtil.getPublicKitId(id));
    }

    ItemStack[] getPublicKit(String id) {
        return KitContents.copy(kits.get(IDUtil.getPublicKitId(id)));
    }

    ItemStack[] peekPublicKit(String id) {
        return kits.get(IDUtil.getPublicKitId(id));
    }

    ItemStack[] getItemStackArrayById(String id) {
        return KitContents.copy(kits.get(id));
    }
}
