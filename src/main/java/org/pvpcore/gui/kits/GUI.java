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
        INSPECT_ENDERCHEST
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
        }
    }

    public static void setEditorContext(Player viewer, EditorContext context) {
        editorContexts.put(viewer.getUniqueId(), context);
    }

    /** Saves the editor the player still has open (if any) before another screen opens. */
    @Override public void flushOpenEditor(Player p) {
        EditorContext context = editorContexts.remove(p.getUniqueId());
        if (context == null) {
            return;
        }
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getSize() == GuiLayouts.MENU_SIZE && top.getLocation() == null) {
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
        }
        GuiChatPrompt.clear();
        GuiSessions.clear();
        // Drop the shared instance so the next one re-initialises the premade store.
        instance = null;
    }
}
