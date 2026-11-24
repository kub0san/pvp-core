package org.pvpcore.features.kits;

import org.pvpcore.util.kits.PlayerUtil;
import org.pvpcore.util.kits.SoundManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.*;

public class KitShareManager {

    public static final int REQUEST_EXPIRY_SECONDS = 120; // why: public timing constant retained for external/legacy compatibility.
    private static final long SHARE_CODE_EXPIRY_TICKS = 15L * 60 * 20; // why: internal expiry constant retained for share-code lifecycle timing.

    /** why: legacy static share-code map retained for external/legacy compatibility; active flows use the singleton instance state. */
    public static HashMap<String, ItemStack[]> kitShareMap;
    private static KitShareManager instance; // why: singleton instance pointer retained for legacy static accessor/lifecycle compatibility.
    private final Plugin plugin;
    private final KitsContext context;
    private final Map<String, ShareRequest> shareRequestsById = new HashMap<>();

    private static final SecureRandom SHARE_CODE_RANDOM = new SecureRandom(); // why: static RNG retained for share-code generation utility behavior.
    private static final char[] SHARE_CODE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray(); // why: static alphabet retained for share-code generation utility behavior.

    /** why: stateless share-code generator utility retained as static helper. */
    private static String newShareCode() {
        char[] code = new char[6];
        for (int i = 0; i < code.length; i++) {
            code[i] = SHARE_CODE_ALPHABET[SHARE_CODE_RANDOM.nextInt(SHARE_CODE_ALPHABET.length)];
        }
        return new String(code);
    }

    public KitShareManager(KitsContext context) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.plugin = context.plugin();
        kitShareMap = new HashMap<>();
        instance = this;
    }

    public KitsContext context() {
        return context;
    }

    /** why: legacy singleton shim retained for external/legacy compatibility; no internal call sites. */
    @Deprecated
    public static KitShareManager get() {
        if (instance == null) {
            throw new IllegalStateException("KitShareManager has not been initialized");
        }
        return instance;
    }

    private KitManager kitManager() {
        return context.kitManager();
    }

    private ItemFilter itemFilter() {
        return context.itemFilter();
    }

    private org.pvpcore.util.kits.Lang lang() {
        return context.lang();
    }

    private org.pvpcore.util.kits.BroadcastManager broadcastManager() {
        return context.broadcastManager();
    }

    private int maxConfiguredSlots() {
        org.bukkit.configuration.file.FileConfiguration cfg = context.config();
        int configured = cfg == null ? 9 : cfg.getInt("kits.max-slots", 9);
        return Math.max(1, Math.min(99, configured));
    }

    public List<String> getKitSlots(Player p) {
        ArrayList<String> slots = new ArrayList<>();
        KitManager km = kitManager();
        if (km == null) return slots;
        for (int i = 1; i <= maxConfiguredSlots(); i++) {
            if (km.hasKit(p.getUniqueId(), i)) {
                slots.add(String.valueOf(i));
            }
        }
        return slots;
    }

    public List<String> getECSlots(Player p) {
        ArrayList<String> slots = new ArrayList<>();
        KitManager km = kitManager();
        if (km == null) return slots;
        for (int i = 1; i <= maxConfiguredSlots(); i++) {
            if (km.hasEC(p.getUniqueId(), i)) {
                slots.add(String.valueOf(i));
            }
        }
        return slots;
    }

    public void shareKit(Player p, int slot) {
        share(p, KitKind.KIT, slot);
    }

    public void shareEC(Player p, int slot) {
        share(p, KitKind.ENDERCHEST, slot);
    }

    private void share(Player p, KitKind kind, int slot) {
        boolean kit = kind == KitKind.KIT;
        KitManager km = kitManager();
        if (km == null) return;
        ItemStack[] contents = km.get(kind, p.getUniqueId(), slot);
        if (contents == null) {
            if (lang() != null) lang().send(p, kit ? "error.kit-not-found" : "error.ec-not-found");
            SoundManager.playFailure(p);
            return;
        }
        String code = newShareCode();
        while (kitShareMap.containsKey(code)) code = newShareCode();
        final String id = code;
        kitShareMap.put(id, contents);

        if (lang() != null) {
            lang().send(p, kit ? "info.share-kit-code" : "info.share-ec-code", "code", id);
            lang().send(p, "info.share-code-expiry");
            lang().send(p, kit ? "info.share-kit-direct-hint" : "info.share-ec-direct-hint");
        }
        SoundManager.playSuccess(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> kitShareMap.remove(id), SHARE_CODE_EXPIRY_TICKS);
    }

    public void copyKit(Player p, String str) {
        String id = str.toUpperCase(java.util.Locale.ROOT);
        if (!kitShareMap.containsKey(id)) {
            if (lang() != null) lang().send(p, "error.kit-expired");
            SoundManager.playFailure(p);
            return;
        }

        ItemFilter filter = itemFilter();
        if (filter == null || !filter.isReady()) {
            if (lang() != null) lang().send(p, "error.kitroom-not-ready");
            return;
        }
        ItemStack[] data = filter.filterKit(kitShareMap.get(id), p);
        if (data == null) return;

        if (data.length == 27) {
            p.getEnderChest().setContents(data);
            if (broadcastManager() != null) broadcastManager().broadcastPlayerCopiedEC(p);
            SoundManager.playSuccess(p);
        } else if (data.length == 41) {
            p.getInventory().setContents(data);
            p.updateInventory();
            if (broadcastManager() != null) broadcastManager().broadcastPlayerCopiedKit(p);
            SoundManager.playSuccess(p);
        } else {
            if (lang() != null) lang().send(p, "error.unexpected");
            SoundManager.playFailure(p);
        }
    }

    public void sendKitShareRequest(Player sender, int slot, Player target) {
        if (!validateTarget(sender, target)) return;
        KitManager km = kitManager();
        if (km == null || !km.hasKit(sender.getUniqueId(), slot)) {
            if (lang() != null) lang().send(sender, "error.kit-not-found");
            SoundManager.playFailure(sender);
            return;
        }
        ShareRequest request = ShareRequest.kit(nextRequestId(), sender.getUniqueId(), sender.getName(),
                target.getUniqueId(), km.getPlayerKit(sender.getUniqueId(), slot).clone());
        registerAndAnnounce(request, sender, target, "info.share-request-kit-received");
    }

    public void sendECShareRequest(Player sender, int slot, Player target) {
        if (!validateTarget(sender, target)) return;
        KitManager km = kitManager();
        if (km == null || !km.hasEC(sender.getUniqueId(), slot)) {
            if (lang() != null) lang().send(sender, "error.ec-not-found");
            SoundManager.playFailure(sender);
            return;
        }
        ShareRequest request = ShareRequest.enderchest(nextRequestId(), sender.getUniqueId(), sender.getName(),
                target.getUniqueId(), km.getPlayerEC(sender.getUniqueId(), slot).clone());
        registerAndAnnounce(request, sender, target, "info.share-request-ec-received");
    }

    public void sendTransferRequest(Player sender, Player target) {
        if (!validateTarget(sender, target)) return;
        KitManager km = kitManager();
        if (km == null) return;
        Map<Integer, ItemStack[]> kits = new LinkedHashMap<>();
        Map<Integer, ItemStack[]> enderchests = new LinkedHashMap<>();
        for (int slot = 1; slot <= maxConfiguredSlots(); slot++) {
            if (km.hasKit(sender.getUniqueId(), slot)) {
                kits.put(slot, km.getPlayerKit(sender.getUniqueId(), slot).clone());
            }
            if (km.hasEC(sender.getUniqueId(), slot)) {
                enderchests.put(slot, km.getPlayerEC(sender.getUniqueId(), slot).clone());
            }
        }

        if (kits.isEmpty() && enderchests.isEmpty()) {
            if (lang() != null) lang().send(sender, "error.nothing-to-transfer");
            SoundManager.playFailure(sender);
            return;
        }

        ShareRequest request = ShareRequest.transfer(nextRequestId(), sender.getUniqueId(), sender.getName(),
                target.getUniqueId(), kits, enderchests);
        registerAndAnnounce(request, sender, target, "info.share-request-transfer-received");
    }

    public void acceptRequest(Player target, @Nullable String idArg) {
        ShareRequest request = resolveRequest(target, idArg);
        if (request == null) return;

        ItemFilter filter = itemFilter();
        if ((request.getType() != ShareRequest.Type.TRANSFER || (filter != null && filter.filtersSaves())) && (filter == null || !filter.isReady())) {
            if (lang() != null) lang().send(target, "error.kitroom-not-ready");
            return;
        }
        ItemStack[] filtered = request.getType() == ShareRequest.Type.TRANSFER ? null : (filter == null ? null : filter.filterKit(request.getContents(), target));
        if (request.getType() != ShareRequest.Type.TRANSFER && filtered == null) return;
        request.cancelExpiryTask();
        shareRequestsById.remove(request.getId());

        Player sender = Bukkit.getPlayer(request.getSenderId());
        switch (request.getType()) {
            case KIT -> {
                target.getInventory().setContents(filtered);
                target.updateInventory();
                if (broadcastManager() != null) broadcastManager().broadcastPlayerCopiedKit(target);
                if (sender != null && lang() != null) lang().send(sender, "success.share-request-accepted", "player", target.getName());
            }
            case ENDERCHEST -> {
                target.getEnderChest().setContents(filtered);
                if (broadcastManager() != null) broadcastManager().broadcastPlayerCopiedEC(target);
                if (sender != null && lang() != null) lang().send(sender, "success.share-request-accepted", "player", target.getName());
            }
            case TRANSFER -> {
                KitManager km = kitManager();
                if (km == null) return;
                int savedKits = 0, savedEnderchests = 0;
                for (var entry : request.getKits().entrySet())
                    if (km.savekit(target.getUniqueId(), entry.getKey(), entry.getValue(), true)) savedKits++;
                for (var entry : request.getEnderchests().entrySet())
                    if (km.saveECSilent(target.getUniqueId(), entry.getKey(), entry.getValue())) savedEnderchests++;
                if (savedKits + savedEnderchests == 0) {
                    if (lang() != null) lang().send(target, "error.item-filter-empty");
                    SoundManager.playFailure(target);
                    return;
                }
                if (lang() != null) lang().send(target, "success.transfer-received", "player", request.getSenderName(), "kits", String.valueOf(savedKits), "ecs", String.valueOf(savedEnderchests));
                if (sender != null && lang() != null) lang().send(sender, "success.transfer-sent", "player", target.getName());
            }
        }
        SoundManager.playSuccess(target);
    }

    public void declineRequest(Player target, @Nullable String idArg) {
        ShareRequest request = resolveRequest(target, idArg);
        if (request == null) return;

        request.cancelExpiryTask();
        shareRequestsById.remove(request.getId());

        if (lang() != null) lang().send(target, "info.share-request-declined-target", "player", request.getSenderName());
        Player sender = Bukkit.getPlayer(request.getSenderId());
        if (sender != null) {
            if (lang() != null) lang().send(sender, "info.share-request-declined-sender", "player", target.getName());
            SoundManager.playFailure(sender);
        }
    }

    public List<String> getPendingRequestIds(Player target) {
        List<String> ids = new ArrayList<>();
        for (ShareRequest request : shareRequestsById.values()) {
            if (request.getTargetId().equals(target.getUniqueId())) {
                ids.add(request.getId());
            }
        }
        return ids;
    }

    private boolean validateTarget(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            if (lang() != null) lang().send(sender, "error.share-with-self");
            SoundManager.playFailure(sender);
            return false;
        }
        return true;
    }

    private void registerAndAnnounce(ShareRequest request, Player sender, Player target, String receivedMessageKey) {
        Iterator<ShareRequest> iterator = shareRequestsById.values().iterator();
        while (iterator.hasNext()) {
            ShareRequest existing = iterator.next();
            if (existing.getSenderId().equals(request.getSenderId())
                    && existing.getTargetId().equals(request.getTargetId())
                    && existing.getType() == request.getType()) {
                existing.cancelExpiryTask();
                iterator.remove();
            }
        }

        shareRequestsById.put(request.getId(), request);

        BukkitTask expiryTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shareRequestsById.remove(request.getId()) == null) return;
                Player senderPlayer = Bukkit.getPlayer(request.getSenderId());
                if (senderPlayer != null && lang() != null) {
                    lang().send(senderPlayer, "info.share-request-expired", "player", PlayerUtil.getPlayerName(request.getTargetId()));
                }
            }
        }.runTaskLater(plugin, REQUEST_EXPIRY_SECONDS * 20L);
        request.setExpiryTask(expiryTask);

        if (lang() != null) {
            lang().send(sender, "info.share-request-sent", "player", target.getName(), "seconds", String.valueOf(REQUEST_EXPIRY_SECONDS));
        }
        SoundManager.playSuccess(sender);

        if (lang() != null) {
            lang().send(target, receivedMessageKey, "player", sender.getName());
            lang().sendNoPrefix(target, "info.share-request-buttons", "id", request.getId());
        }
        SoundManager.playSuccess(target);
    }

    private @Nullable ShareRequest resolveRequest(Player target, @Nullable String idArg) {
        if (idArg == null || idArg.isBlank()) {
            List<ShareRequest> pending = findRequestsForTarget(shareRequestsById, target.getUniqueId());
            if (pending.isEmpty()) {
                if (lang() != null) lang().send(target, "error.share-no-pending-requests");
                SoundManager.playFailure(target);
                return null;
            }
            if (pending.size() > 1) {
                if (lang() != null) lang().send(target, "error.share-multiple-pending-requests");
                SoundManager.playFailure(target);
                return null;
            }
            return pending.get(0);
        }

        ShareRequest request = findRequestById(shareRequestsById, target.getUniqueId(), idArg);
        if (request == null) {
            if (lang() != null) lang().send(target, "error.share-request-not-found");
            SoundManager.playFailure(target);
            return null;
        }
        return request;
    }

    static List<ShareRequest> findRequestsForTarget(Map<String, ShareRequest> requests, UUID targetId) {
        List<ShareRequest> pending = new ArrayList<>();
        for (ShareRequest request : requests.values()) {
            if (request.getTargetId().equals(targetId)) pending.add(request);
        }
        return pending;
    }

    static @Nullable ShareRequest findRequestById(Map<String, ShareRequest> requests, UUID targetId, String id) {
        ShareRequest request = requests.get(id.toUpperCase(java.util.Locale.ROOT));
        if (request == null || !request.getTargetId().equals(targetId)) return null;
        return request;
    }

    private String nextRequestId() {
        String id;
        do {
            id = newShareCode();
        } while (shareRequestsById.containsKey(id));
        return id;
    }

    /** Cancels pending expiries and drops all cached state so a reload starts clean. */
    public static void shutdown() {
        KitShareManager manager = instance;
        if (manager != null) {
            for (ShareRequest request : manager.shareRequestsById.values()) {
                request.cancelExpiryTask();
            }
            manager.shareRequestsById.clear();
        }
        if (kitShareMap != null) {
            kitShareMap.clear();
        }
        instance = null;
    }
}