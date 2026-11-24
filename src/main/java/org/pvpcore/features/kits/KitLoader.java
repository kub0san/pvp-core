package org.pvpcore.features.kits;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.util.kits.BroadcastManager;
import org.pvpcore.util.kits.IDUtil;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.SoundManager;

/**
 * Kit/enderchest load pipeline. Package-private helper owned by {@link KitManager};
 * not exposed through {@link KitsContext}.
 */
final class KitLoader {
    private final KitsContext context;
    private final KitCache cache;
    private final PlayerKitSession sessions;

    KitLoader(KitsContext context, KitCache cache, PlayerKitSession sessions) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.cache = java.util.Objects.requireNonNull(cache, "cache");
        this.sessions = java.util.Objects.requireNonNull(sessions, "sessions");
    }

    private org.bukkit.configuration.file.FileConfiguration config() {
        return context.config();
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

    boolean filterReady(Player player) {
        if (itemFilter().isReady()) return true;
        lang().send(player, "error.kitroom-not-ready");
        return false;
    }

    boolean loadKitInternal(Player player, String kitId, Runnable notFoundMessage, boolean isEnderChest, Runnable afterLoad) {
        if (player == null) {
            return false;
        }

        ItemStack[] kit = cache.getRaw(kitId);
        if (kit == null) {
            if (notFoundMessage != null) {
                notFoundMessage.run();
                SoundManager.playFailure(player);
            }
            return false;
        }

        if (!filterReady(player)) return false;
        kit = itemFilter().filterKit(kit, notFoundMessage == null ? null : player);
        if (kit == null) return false;
        if (isEnderChest) {
            player.getEnderChest().setContents(kit);
        } else {
            player.getInventory().setContents(kit);
            // Force a client-side resync of the whole inventory (including the
            // offhand slot) so the client doesn't keep rendering a stale item,
            // e.g. a "ghost" totem in the offhand on the death screen.
            player.updateInventory();
        }

        if (afterLoad != null) {
            afterLoad.run();
        }
        SoundManager.playSuccess(player);
        KitItemSupport.applyLoadEffects(player, isEnderChest, config());
        return true;
    }

    boolean loadKit(Player player, int slot) {
        if (player == null) {
            return false;
        }
        return loadKitInternal(player, IDUtil.getPlayerKitId(player.getUniqueId(), slot),
                () -> lang().send(player, "error.kit-slot-not-found", "slot", String.valueOf(slot)),
                false, () -> {
                    broadcastManager().broadcastPlayerLoadedPrivateKit(player, "Kit " + slot);
                    lang().send(player, "success.kit-loaded", "slot", String.valueOf(slot));
                    sessions.rememberKit(player.getUniqueId(), new KitManager.KitReference(slot, null));
                });
    }

    boolean loadKitSilent(Player player, int slot) {
        if (player == null) {
            return false;
        }
        return loadKitInternal(player, IDUtil.getPlayerKitId(player.getUniqueId(), slot), null, false, null);
    }

    boolean loadPublicKit(Player player, String id) {
        PublicKit publicKit = cache.findPublicKit(id);
        String kitDisplayName = publicKit != null ? publicKit.name : id;
        return loadKitInternal(player, IDUtil.getPublicKitId(id),
                () -> lang().send(player, "error.kit-not-found"),
                false, () -> {
                    sessions.rememberKit(player.getUniqueId(), new KitManager.KitReference(null, id));
                    broadcastManager().broadcastPlayerLoadedPublicKit(player, kitDisplayName);
                    lang().send(player, "success.public-kit-loaded");
                    lang().send(player, "info.custom-version-available");
                });
    }

    boolean loadPublicKitSilent(Player player, String id) {
        return loadKitInternal(player, IDUtil.getPublicKitId(id), null, false, null);
    }

    boolean loadEnderchest(Player player, int slot) {
        if (player == null) {
            return false;
        }
        return loadKitInternal(player, IDUtil.getECId(player.getUniqueId(), slot),
                () -> lang().send(player, "error.kit-slot-not-found", "slot", String.valueOf(slot)),
                true, () -> {
                    sessions.rememberEnderchest(player.getUniqueId(), slot);
                    broadcastManager().broadcastPlayerLoadedEnderChest(player);
                    lang().send(player, "success.ec-loaded", "slot", String.valueOf(slot));
                });
    }

    boolean loadEnderchestSilent(Player player, int slot) {
        if (player == null) {
            return false;
        }
        return loadKitInternal(player, IDUtil.getECId(player.getUniqueId(), slot), null, true, null);
    }

    boolean loadLastKit(Player player) {
        if (player == null) return false;
        KitManager.KitReference ref = sessions.getLastKitReference(player.getUniqueId());
        if (ref == null) return false;
        return ref.publicId() == null ? loadKitSilent(player, ref.slot()) : loadPublicKitSilent(player, ref.publicId());
    }
}
