package org.pvpcore.gui.kits;

import org.pvpcore.gui.base.GuiChatPrompt;
import org.pvpcore.gui.kits.editor.EditorSaver;
import org.pvpcore.gui.base.GuiConfirm;
import org.pvpcore.gui.kits.data.PremadeStore;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.kits.editor.ItemEditorPage;
import org.pvpcore.gui.kits.editor.KitRoomEditorPage;
import org.pvpcore.gui.kits.page.KitListPage;
import org.pvpcore.gui.kits.page.KitRoomPageSettingsPage;
import org.pvpcore.gui.kits.page.KitRoomPagesPage;
import org.pvpcore.gui.kits.page.PremadeKitSettingsPage;
import org.pvpcore.gui.kits.page.PremadePage;
import org.pvpcore.gui.kits.page.PreviewPage;
import org.pvpcore.gui.kits.page.KitRoomPage;
import org.pvpcore.gui.kits.page.StaffPage;
import org.pvpcore.gui.base.GuiSession;
import org.pvpcore.gui.base.GuiSessions;

import org.pvpcore.features.kits.ItemFilter;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.features.kits.Kits;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.util.kits.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.pvpcore.util.kits.PlayerUtil.getPlayerName;

/**
 * Entry point for every kits screen. The screens themselves live in
 * org.pvpcore.gui.base (the foundation), org.pvpcore.gui.kits.page and
 * org.pvpcore.gui.kits.editor, all built with the IF framework and the shared
 * GuiTheme. This class keeps the shared editor state: which editor a player has
 * open, the clear and deletion flags, and the last My Kits page.
 */
public class GUI implements GuiSession {
    private static GUI instance;
    private final Plugin plugin;
    private final KitsContext kits;
    private static final Set<UUID> kitDeletionFlag = new HashSet<>();
    private static final Set<UUID> clearedEditors = new HashSet<>();
    private static final Map<UUID, EditorContext> editorContexts = new HashMap<>();
    private static final Map<UUID, Integer> lastMainMenuPage = new HashMap<>();
    private final Map<UUID, Map<String, org.pvpcore.gui.base.GuiSettingsSupport.Staged>> stagedSettings = new ConcurrentHashMap<>();
    // Instance state, per ADR 0008: pending rename/chat operations and reload invalidation.
    private final Map<UUID, PendingCustomize> pendingCustomize = new ConcurrentHashMap<>();
    private final Set<UUID> staleCustomize = ConcurrentHashMap.newKeySet();
    // Refcounted: switching between customize, rename and banner screens must not
    // untrack a viewer while another of those screens is still open.
    private final Map<UUID, Integer> openCustomizeScreens = new ConcurrentHashMap<>();

    public static boolean takeClearFlag(Player player) { return clearedEditors.remove(player.getUniqueId()); }
    public static void markKitDeleted(Player player) { kitDeletionFlag.add(player.getUniqueId()); }
    public static void markCleared(Player player) { clearedEditors.add(player.getUniqueId()); }
    public static void rememberMainMenuPage(Player player, int page) { lastMainMenuPage.put(player.getUniqueId(), page); }
    public static int lastMainMenuPageOf(Player player) { return lastMainMenuPage.getOrDefault(player.getUniqueId(), 0); }
    @Override public Plugin plugin() { return plugin; }
    @Override public KitsContext kits() { return kits; }
    @Override public Map<UUID, Map<String, org.pvpcore.gui.base.GuiSettingsSupport.Staged>> stagedSettingsStore() { return stagedSettings; }

    public enum EditorType {
        KIT,
        PUBLIC_KIT,
        ENDERCHEST,
        INSPECT_KIT,
        INSPECT_ENDERCHEST;

        /** True for the compact ender-chest variants. */
        public boolean enderchest() {
            return this == ENDERCHEST || this == INSPECT_ENDERCHEST;
        }

        /** Top-inventory size this editor must have before its contents may be saved. */
        public int topSize() {
            return (enderchest() ? GuiLayouts.ROWS_EDITOR_EC : GuiLayouts.ROWS_EDITOR_KIT)
                    * GuiLayouts.COLUMNS;
        }

        /** First editable content slot of this editor. */
        public int contentStart() {
            return enderchest() ? GuiLayouts.EC_CONTENT_START : GuiLayouts.KIT_CONTENT_START;
        }

        /** Exclusive end of the editable content range of this editor. */
        public int contentEnd() {
            return enderchest() ? GuiLayouts.EC_CONTENT_END : GuiLayouts.KIT_CONTENT_END;
        }
    }

    public record EditorContext(EditorType type, int slot, String id, UUID target, String playerName) {
    }

    public static EditorContext getAndRemoveEditorContext(UUID viewer) {
        return editorContexts.remove(viewer);
    }

    public static void forgetMainMenuPage(UUID player) {
        lastMainMenuPage.remove(player);
        clearedEditors.remove(player);
        editorContexts.remove(player);
        PremadePage.forget(player);
        if (instance != null) {
            instance.stagedSettings.remove(player);
            instance.clearPendingCustomize(player);
            instance.openCustomizeScreens.remove(player);
        }
    }

    public static void setEditorContext(Player viewer, EditorContext context) {
        editorContexts.put(viewer.getUniqueId(), context);
    }

    // ------------------------------------------------------------------ pending cosmetic sessions

    /** A pending chat/async operation for one viewer, identified so stale callbacks are ignored. */
    public static final class PendingCustomize {
        private final UUID operationId;
        private final CustomizeSession session;
        private volatile boolean stale;

        private PendingCustomize(UUID operationId, CustomizeSession session) {
            this.operationId = operationId;
            this.session = session;
        }

        public UUID operationId() { return operationId; }
        public CustomizeSession session() { return session; }
        public boolean stale() { return stale; }
        private void markStale() { stale = true; }
    }

    /**
     * Stores a session for an async prompt and returns the id the callback must present.
     *
     * <p>Starting a chat/async prompt is not a fresh editor entry: this deliberately keeps any
     * reload stale marker. Only {@code CustomizePage.open} clears stale state; a pending
     * operation must not.</p>
     */
    public UUID setPendingCustomize(UUID viewer, CustomizeSession session) {
        return holdPendingCustomize(viewer, session);
    }

    /**
     * Stores a queued-save session without clearing a reload stale marker. A stale screen must
     * still be rejected by the Apply check and by {@link #takePendingCustomize}.
     */
    public UUID holdPendingCustomize(UUID viewer, CustomizeSession session) {
        if (viewer == null) throw new IllegalArgumentException("viewer");
        if (session == null) throw new IllegalArgumentException("session");
        UUID operationId = UUID.randomUUID();
        pendingCustomize.put(viewer, new PendingCustomize(operationId, session));
        return operationId;
    }

    /** Takes the session only when the operation id matches; stale/mismatched callbacks get null. */
    public CustomizeSession takePendingCustomize(UUID viewer, UUID operationId) {
        if (viewer == null || operationId == null || isCustomizeStale(viewer)) return null;
        PendingCustomize pending = pendingCustomize.get(viewer);
        if (pending == null || pending.stale() || !operationId.equals(pending.operationId())) return null;
        pendingCustomize.remove(viewer, pending);
        return pending.session();
    }

    // ------------------------------------------------------------------ open customize screens

    /** Tracks one more open customize-family screen for this viewer. */
    public void registerCustomizeScreen(UUID viewer) {
        if (viewer == null) return;
        openCustomizeScreens.merge(viewer, 1, Integer::sum);
    }

    /** Tracks one fewer open screen; a viewer stays tracked while another screen is still open. */
    public void unregisterCustomizeScreen(UUID viewer) {
        if (viewer == null) return;
        openCustomizeScreens.computeIfPresent(viewer, (key, count) -> count <= 1 ? null : count - 1);
    }

    /** Number of customize-family screens currently open for this viewer. */
    public int customizeScreenCount(UUID viewer) {
        return viewer == null ? 0 : openCustomizeScreens.getOrDefault(viewer, 0);
    }

    /**
     * True when no other customize-family screen is open for the viewer. A screen checks this
     * after unregistering itself: during a tab switch the incoming screen is registered first,
     * so its close handler must not discard the session's staged changes.
     */
    public boolean isLastCustomizeScreen(UUID viewer) {
        return customizeScreenCount(viewer) <= 0;
    }

    public void clearPendingCustomize(UUID viewer) {
        if (viewer == null) return;
        pendingCustomize.remove(viewer);
        staleCustomize.remove(viewer);
    }

    /** A reload invalidates every pending operation and open screen; later Apply calls are rejected. */
    public void invalidateCustomizeSessions() {
        for (Map.Entry<UUID, PendingCustomize> entry : pendingCustomize.entrySet()) {
            entry.getValue().markStale();
            staleCustomize.add(entry.getKey());
        }
        pendingCustomize.clear();
        // Open screens live outside the pending map; mark their viewers too so an Apply from
        // a screen opened before the reload is refused until the editor is reopened.
        for (UUID viewer : openCustomizeScreens.keySet()) {
            staleCustomize.add(viewer);
        }
    }

    public boolean isCustomizeStale(UUID viewer) {
        return viewer != null && staleCustomize.contains(viewer);
    }

    public void markCustomizeStale(UUID viewer) {
        if (viewer != null) staleCustomize.add(viewer);
    }

    public void clearCustomizeStale(UUID viewer) {
        if (viewer != null) staleCustomize.remove(viewer);
    }

    /** Saves the editor the player still has open (if any) before another screen opens. */
    @Override public void flushOpenEditor(Player p) {
        EditorContext context = editorContexts.remove(p.getUniqueId());
        if (context == null) {
            return;
        }
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getSize() == context.type().topSize() && top.getLocation() == null) {
            EditorSaver.save(kits, p, context, top);
        }
    }

    /**
     * The one shared GUI instance. Every field except the plugin/context references is
     * static, so a second instance would be a copy with no state of its own.
     *
     * <p>Safe to call from anywhere: the first call constructs the instance,
     * later calls return the same object no matter which plugin/context is passed.</p>
     */
    public static GUI getOrCreate(Plugin plugin, KitsContext kits) {
        if (instance == null) {
            new GUI(plugin, kits);
        }
        return instance;
    }

    /** The shared instance. Throws when no GUI has been created yet. */
    public static GUI get() {
        if (instance == null) {
            throw new IllegalStateException("GUI has not been created yet");
        }
        return instance;
    }

    /**
     * Prefer {@link #getOrCreate(Plugin, KitsContext)}.
     *
     * <p>The constructor stays public for compatibility and is safe
     * to call repeatedly -- it only initialises shared state on the
     * first instance.</p>
     */
    public GUI(Plugin plugin, KitsContext kits) {
        this.plugin = plugin;
        this.kits = kits;
        if (instance == null) {
            instance = this;
            GuiSessions.set(this);
            PremadeStore.init(plugin, kits);
        }
    }

    public static boolean removeKitDeletionFlag(Player player) {
        return kitDeletionFlag.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ editors

    @Deprecated public void OpenKitMenu(Player p, int slot) { openKitEditor(p, slot); }
    @Deprecated public void OpenECKitKenu(Player p, int slot) { openEnderchestEditor(p, slot); }

    public void openKitEditor(Player p, int slot) {
        if (!(ActionGuards.allowed(p, "perplayerkit.kit") && ActionGuards.dataReady(p))) return;
        if (!kits.itemFilter().isReady()) { kits.lang().send(p, "error.kitroom-not-ready"); return; }
        ItemEditorPage.openKit(this, p, slot);
    }

    public void OpenPublicKitEditor(Player p, String kitId) {
        if (!(ActionGuards.allowed(p, "perplayerkit.admin"))) return;
        ItemEditorPage.openPublicKit(this, p, kitId);
    }

    public void openEnderchestEditor(Player p, int slot) {
        if (!(ActionGuards.allowed(p, "perplayerkit.enderchest") && ActionGuards.dataReady(p))) return;
        if (!kits.itemFilter().isReady()) { kits.lang().send(p, "error.kitroom-not-ready"); return; }
        ItemEditorPage.openEnderchest(this, p, slot);
    }

    public void InspectKit(Player p, UUID target, int slot) {
        String playerName = getPlayerName(target);
        if (playerName == null) {
            playerName = target.toString();
        }
        ItemEditorPage.openInspectKit(this, p, target, playerName, slot);
    }

    public void InspectEc(Player p, UUID target, int slot) {
        String playerName = getPlayerName(target);
        if (playerName == null) {
            playerName = target.toString();
        }
        ItemEditorPage.openInspectEnderchest(this, p, target, playerName, slot);
    }

    // ------------------------------------------------------------------ player screens

    public void OpenMainMenu(Player p) {
        OpenMainMenu(p, 0);
    }

    public void OpenMainMenu(Player p, int page) {
        if (!(ActionGuards.allowed(p, "perplayerkit.menu") && ActionGuards.dataReady(p))) return;
        KitListPage.open(this, p, page);
    }

    public void OpenKitRoom(Player p) {
        OpenKitRoom(p, 0);
    }

    public void OpenKitRoom(Player p, int page) {
        if (!ActionGuards.allowed(p, "perplayerkit.menu", LocationFeature.KIT_ROOM)) return;
        KitRoomPage.open(this, p, page);
    }

    public void ViewPublicKitMenu(Player p, String id) {
        if (!(ActionGuards.allowed(p, "perplayerkit.publickit"))) return;
        ItemStack[] kit = kits.itemFilter().filterItemStack(kits.kitManager().getPublicKit(id));

        if (kit == null) {
            kits.lang().send(p, "error.kit-not-found-display");
            if (p.hasPermission("perplayerkit.admin")) {
                kits.lang().send(p, "info.assign-publickit-instruction");
            }
            return;
        }
        new PreviewPage(this, p, id, kit).show();
    }

    public void OpenPublicKitMenu(Player player) { OpenPublicKitMenu(player, 0); }

    public void OpenPublicKitMenu(Player player, int page) {
        if (!ActionGuards.allowed(player, "perplayerkit.publickit")) return;
        PremadePage.open(this, player);
    }

    // ------------------------------------------------------------------ admin screens

    public void OpenMainManagerMenu(Player p) {
        StaffPage.open(this, p);
    }

    public void OpenKitRoomManager(Player p) {
        KitRoomPagesPage.open(this, p);
    }

    public void OpenKitRoomPageSettings(Player p, int pageIndex) {
        new KitRoomPageSettingsPage(this, p, pageIndex).show();
    }

    public void OpenKitRoomItemEditor(Player p, int pageIndex) {
        KitRoomEditorPage.open(this, p, pageIndex);
    }

    public void OpenPremadeKitsManager(Player p, int page) {
        PremadePage.open(this, p);
    }

    public void OpenPremadeKitSettings(Player p, String kitId) {
        PremadeKitSettingsPage.open(this, p, kitId);
    }

    public void OpenConfirmMenu(Player p, String title, Runnable onConfirm, Runnable onCancel) {
        GuiConfirm.open(p, title, onConfirm, onCancel);
    }

    public static void shutdown() {
        editorContexts.clear();
        lastMainMenuPage.clear();
        kitDeletionFlag.clear();
        clearedEditors.clear();
        if (instance != null) {
            instance.stagedSettings.clear();
            // Pending operations die with the instance; the stale markers stay so an old screen
            // held by a live callback can still reject Apply after a reload.
            instance.pendingCustomize.clear();
            instance.openCustomizeScreens.clear();
        }
        GuiChatPrompt.clear();
        GuiSessions.clear();
        // Drop the shared instance so the next one re-initialises the premade store.
        instance = null;
    }
}
