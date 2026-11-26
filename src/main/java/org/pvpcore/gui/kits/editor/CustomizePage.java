package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.cosmetics.ApplyResult;
import org.pvpcore.features.kits.cosmetics.CosmeticApplier;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.features.kits.cosmetics.CustomizeService;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiChatPrompt;
import org.pvpcore.gui.base.GuiClick;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One-row hub for the single kit item selected by a shift-click in the editor.
 *
 * <p>There is no 41-slot target picker and no mode tabs: the selected slot stays the session
 * target, and every action (rename, enchant, trim, apply, cancel) acts on that one item. The
 * screen is deliberately read-only and 27 slots so it cannot be used to shuffle kit contents.</p>
 */
public final class CustomizePage extends GuiScreen {
    private static final int HUB_ROWS = 3;
    private static final int HUB_ROW = 1;
    private static final int RENAME_COL = 0;
    private static final int ENCHANT_COL = 2;
    private static final int TRIM_COL = 4;
    private static final int APPLY_COL = 6;
    private static final int CANCEL_COL = 8;

    private final GUI gui;
    private final int kitSlot;
    private final int slot;
    private final CustomizeSession session;

    private boolean writePending;
    private boolean chatPending;

    private CustomizePage(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        super(gui, player, HUB_ROWS, GuiFrame.Density.NONE, "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.slot = slot;
        this.kitSlot = kitSlot;
        // A reopened session keeps its selection; a fresh hub entry selects the clicked item.
        if (session != null && slot >= 0 && slot < GuiLayouts.KIT_CONTENT_END) {
            session.selectTarget(slot);
        }
        gui.registerCustomizeScreen(player.getUniqueId());
    }

    /** Fresh entry from the editor: a previous reload marker no longer applies. */
    public static void open(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        if (!canOpen(gui, player, session)) return;
        gui.clearCustomizeStale(player.getUniqueId());
        new CustomizePage(gui, player, session, slot, kitSlot).show();
    }

    /** Reopens the hub with an existing session (rename return, save failure); stale state stays. */
    public static void reopen(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        if (!canOpen(gui, player, session)) return;
        new CustomizePage(gui, player, session, slot, kitSlot).show();
    }

    private static boolean canOpen(GUI gui, Player player, CustomizeSession session) {
        if (gui == null || player == null || session == null) return false;
        if (!ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return false;
        if (gui.kits() == null) return false;
        if (!gui.kits().itemFilter().isReady()) {
            gui.kits().lang().send(player, "error.kitroom-not-ready");
            return false;
        }
        if (!rulesOf(gui).enabled()) {
            gui.kits().lang().send(player, "error.cosmetics-disabled");
            return false;
        }
        return true;
    }

    @Override
    protected String title() {
        if (kits() == null) return "Customize Item";
        // GuiScreen expects a legacy title; sectionTitle converts MiniMessage so the brand
        // colour and bold actually render instead of showing literal tags in-game.
        return GuiMenuFactory.sectionTitle(kits(), GuiTheme.ACCENT + "<bold>Customize Item");
    }

    private static CustomizeRules rulesOf(GUI gui) {
        return CustomizeRules.parse(gui == null || gui.kits() == null || gui.kits().config() == null
                ? null : gui.kits().config().getConfigurationSection("customize"));
    }

    private static void send(GUI gui, Player player, String key) {
        if (gui != null && gui.kits() != null) gui.kits().lang().send(player, key);
    }

    /** Trims are only offered for genuine armor; shields and weapons must never see the button. */
    public static boolean supportsTrim(ItemStack item) {
        return new CosmeticValidator(CustomizeRules.parse(null)).isArmor(item);
    }

    @Override
    protected void renderContent(StaticPane content) {
        // No content picker: the item is selected in the editor before this screen opens.
    }

    private enum Action { RENAME, ENCHANT, TRIM, APPLY, CANCEL }

    /** Testable description of the one-row hub: column, icon and the action it performs. */
    record HubButton(int column, Material icon, Action action) {
    }

    static List<HubButton> hubButtons(CustomizeSession session) {
        List<HubButton> buttons = new ArrayList<>();
        buttons.add(new HubButton(RENAME_COL, Material.NAME_TAG, Action.RENAME));
        buttons.add(new HubButton(ENCHANT_COL, Material.ENCHANTED_BOOK, Action.ENCHANT));
        if (session != null && supportsTrim(session.target())) {
            buttons.add(new HubButton(TRIM_COL, Material.NETHERITE_INGOT, Action.TRIM));
        }
        buttons.add(new HubButton(APPLY_COL, Material.LIME_DYE, Action.APPLY));
        buttons.add(new HubButton(CANCEL_COL, Material.BARRIER, Action.CANCEL));
        return List.copyOf(buttons);
    }

    private static ItemStack buttonItem(HubButton button) {
        return switch (button.action()) {
            case RENAME -> GuiTheme.named(button.icon(), GuiTheme.AQUA + "<bold>", "Rename",
                    GuiTheme.dim("Change this item's name"), GuiTheme.hint("Click", "type a name"));
            case ENCHANT -> GuiTheme.named(button.icon(), GuiTheme.AQUA + "<bold>", "Enchant",
                    GuiTheme.dim("Edit enchantments"), GuiTheme.hint("Click", "open"));
            case TRIM -> GuiTheme.named(button.icon(), GuiTheme.AQUA + "<bold>", "Trim",
                    GuiTheme.dim("Edit armor trim"), GuiTheme.hint("Click", "open"));
            case APPLY -> GuiTheme.named(button.icon(), GuiTheme.GREEN + "<bold>", "Apply",
                    GuiTheme.dim("Commit staged changes"), GuiTheme.hint("Click", "save"));
            case CANCEL -> GuiTheme.named(button.icon(), GuiTheme.RED + "<bold>", "Cancel",
                    GuiTheme.dim("Discard changes and go back"), GuiTheme.hint("Click", "back"));
        };
    }

    private GuiClick handler(Action action) {
        return switch (action) {
            case RENAME -> (p, e) -> openRename(p);
            case ENCHANT -> (p, e) -> openEnchantments(p);
            case TRIM -> (p, e) -> openTrims(p);
            case APPLY -> (p, e) -> apply(p);
            case CANCEL -> (p, e) -> cancel(p);
        };
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        for (HubButton button : hubButtons(session)) {
            GuiClick action = handler(button.action());
            chrome.addItem(wrap(buttonItem(button), (p, e) -> {
                SoundManager.playClick(p);
                action.on(p, e);
            }, player), button.column(), HUB_ROW);
        }
    }

    // ------------------------------------------------------------------ actions

    private void openRename(Player p) {
        if (!rulesOf(gui).enabled()) {
            send(gui, p, "error.cosmetics-disabled");
            return;
        }
        UUID viewer = p.getUniqueId();
        if (gui.isCustomizeStale(viewer)) {
            send(gui, p, "error.cosmetics-kit-changed");
            return;
        }
        ItemStack target = session.target();
        if (target == null || target.getType().isAir()) {
            send(gui, p, "error.cosmetics-name-invalid");
            return;
        }
        UUID operationId = gui.holdPendingCustomize(viewer, session);
        // The prompt closes this screen; keep its staged edits alive until the callback arrives.
        chatPending = true;
        GuiChatPrompt.ask(gui.plugin(), p, "Type a new name (MiniMessage), or 'cancel'",
                text -> {
                    chatPending = false;
                    CustomizeSession taken = stageRename(gui, p, operationId, text);
                    if (taken != null) CustomizePage.reopen(gui, p, taken, slot, kitSlot);
                },
                () -> cancelRename(p, operationId));
    }

    /**
     * Consumes the pending rename operation and stages the parsed name on its selected target.
     * A stale/mismatched operation returns null so no screen can be revived after a reload.
     */
    static CustomizeSession stageRename(GUI gui, Player player, UUID operationId, String text) {
        if (gui == null || player == null || operationId == null) return null;
        CustomizeSession taken = gui.takePendingCustomize(player.getUniqueId(), operationId);
        if (taken == null) return null;
        ItemStack current = taken.target();
        if (current == null || current.getType().isAir()) return taken;
        CustomizeRules rules = rulesOf(gui);
        CosmeticApplier applier = new CosmeticApplier(rules, new CosmeticValidator(rules));
        try {
            taken.stageTarget(applier.applyName(current, text));
        } catch (CosmeticValidator.BlockedTagException ex) {
            send(gui, player, "error.cosmetics-name-tag-blocked");
        } catch (IllegalArgumentException ex) {
            send(gui, player, "error.cosmetics-name-invalid");
        }
        return taken;
    }

    /** Cancels a pending rename; a stale callback must neither reopen nor consume the operation. */
    boolean cancelRename(Player p, UUID operationId) {
        chatPending = false;
        CustomizeSession taken = gui.takePendingCustomize(p.getUniqueId(), operationId);
        if (taken == null) return false;
        CustomizePage.reopen(gui, p, taken, slot, kitSlot);
        return true;
    }

    /** Placeholder: the compact enchantment screen replaces this in the next task. */
    private void openEnchantments(Player p) {
        // The compact enchantment screen replaces this placeholder in the next task.
    }

    /** Placeholder: the compact trim screen replaces this in a later task. */
    private void openTrims(Player p) {
        if (!supportsTrim(session.target())) send(gui, p, "error.cosmetics-not-armor");
    }

    private void apply(Player p) {
        if (writePending || chatPending) return;
        CustomizeRules rules = rulesOf(gui);
        if (!rules.enabled()) {
            send(gui, p, "error.cosmetics-disabled");
            return;
        }
        UUID viewer = p.getUniqueId();
        if (gui.isCustomizeStale(viewer)) {
            send(gui, p, "error.cosmetics-kit-changed");
            return;
        }
        CosmeticValidator validator = new CosmeticValidator(rules);
        for (ItemStack item : session.working()) {
            if (validator.isShield(item) && validator.hasUnreadableBannerLayers(item)) {
                send(gui, p, "error.cosmetics-banner-locked");
                return;
            }
        }
        // Register without clearing a reload stale marker, then commit through the
        // outcome overload so the queued write can report back after close/quit.
        UUID operationId = gui.holdPendingCustomize(viewer, session);
        CustomizeService service = CustomizeService.create(gui);
        ApplyResult result = service.commit(p, kitSlot, session, success ->
                Bukkit.getScheduler().runTask(gui.plugin(), () -> {
                    CustomizeSession taken = gui.takePendingCustomize(viewer, operationId);
                    if (taken == null) return; // consumed, mismatched, or invalidated by reload
                    if (!p.isOnline()) {
                        if (!success) {
                            gui.plugin().getLogger().warning(
                                    "Late kit cosmetics save failure for " + viewer);
                        }
                        return;
                    }
                    if (success) {
                        send(gui, p, "info.cosmetics-applied");
                        ItemEditorPage.openKit(gui, p, kitSlot);
                    } else {
                        // The write was queued but not confirmed. The cache already holds the
                        // staged result, so rebase onto it: otherwise a retry is rejected as
                        // KIT_CHANGED forever. A null manager or cache read keeps the taken
                        // session (and its staged edits) intact.
                        send(gui, p, "error.cosmetics-save-unconfirmed");
                        KitManager manager = gui.kits() == null ? null : gui.kits().kitManager();
                        ItemStack[] current = manager == null
                                ? null : manager.getPlayerKit(viewer, kitSlot);
                        CustomizePage.reopen(gui, p, taken.rebaseFrom(current), slot, kitSlot);
                    }
                }));
        if (result == ApplyResult.QUEUED) {
            writePending = true;
            send(gui, p, "info.cosmetics-save-queued");
            return; // the outcome callback closes the screen once the write resolves
        }
        gui.clearPendingCustomize(viewer);
        switch (result) {
            case PERMISSION_DENIED -> send(gui, p, "error.cosmetics-disabled");
            case KIT_CHANGED -> send(gui, p, "error.cosmetics-kit-changed");
            case INVALID_CONTENT -> send(gui, p, "error.cosmetics-name-invalid");
            case STORAGE_FAILED -> send(gui, p, "error.cosmetics-save-failed");
            case QUEUED -> { /* handled above */ }
        }
    }

    private void cancel(Player p) {
        gui.clearPendingCustomize(p.getUniqueId());
        session.discard();
        gui.flushOpenEditor(p);
        ItemEditorPage.openKit(gui, p, kitSlot);
    }

    @Override
    protected void protect(ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(ChestGui chest) {
        chest.setOnClose(event -> {
            if (!(event.getPlayer() instanceof Player closer)) return;
            UUID viewer = closer.getUniqueId();
            gui.unregisterCustomizeScreen(viewer);
            if (writePending) {
                // The queued write keeps its clone; only feedback and pending state are dropped.
                gui.clearPendingCustomize(viewer);
            } else if (chatPending) {
                // GuiChatPrompt holds the session until the typed name (or cancel) arrives.
                return;
            } else if (gui.isLastCustomizeScreen(viewer)) {
                session.discard();
            }
        });
    }
}
