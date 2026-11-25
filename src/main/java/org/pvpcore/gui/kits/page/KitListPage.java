package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.features.kits.KitKind;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.BroadcastManager;
import org.pvpcore.util.kits.KitSlots;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.PlayerUtil;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The main dashboard. 5 bordered rows:
 *
 * <pre>
 *  row 0   top glass frame
 *  row 1   kit cards          (Kit 1..9 of the current page, fixed positions)
 *  row 2   ender chest cards  (same slot numbers, same columns)
 *  row 3   quick actions      (kit room, premade, repair, staff)
 *  row 4   dock               (pager in the middle, clear inventory on the right)
 * </pre>
 *
 * Every slot has a fixed column: Kit 3 is always the third card on its page, so
 * muscle memory works. An unused slot is a dim "empty" card that opens the editor.
 * One pager flips kits and ender chests together.
 */
public final class KitListPage extends GuiScreen {
    private static final String MENU = "perplayerkit.menu";

    private final GUI gui;
    private int page;

    private KitListPage(GUI gui, Player player, int page) {
        super(gui, player, GuiLayouts.ROWS_DASHBOARD, GuiFrame.Density.BORDER, MENU, LocationFeature.MENU);
        this.gui = gui;
        this.page = Math.max(0, page);
    }

    public static void open(GUI gui, Player player, int page) {
        KitListPage screen = new KitListPage(gui, player, page);
        screen.page = screen.pager().currentPage();
        GUI.rememberMainMenuPage(player, screen.page);
        screen.show();
    }

    /** The dashboard page a slot lives on (slots are 1-based, nine per page). */
    public static int pageOfSlot(Player player, boolean enderchest, int slot) {
        return Math.max(0, (slot - 1) / GuiLayouts.DASH_SLOTS_PER_PAGE);
    }

    /**
     * Inclusive 1-based slot range painted on one dashboard page. Immutable by virtue
     * of being a record, and package-private so the range can be tested without a server.
     * The page is clamped to the same valid range as the pager; {@code maxKits} is
     * expected to arrive already clamped to 1..99 by KitsContext.maxKits().
     */
    record VisibleSlots(int first, int last) {
    }

    static VisibleSlots visibleSlots(int page, int maxKits) {
        int perPage = GuiLayouts.DASH_SLOTS_PER_PAGE;
        int maxPages = Math.max(1, (maxKits + perPage - 1) / perPage);
        int clampedPage = Math.max(0, Math.min(page, maxPages - 1));
        int first = clampedPage * perPage + 1;
        int last = Math.min(maxKits, first + perPage - 1);
        return new VisibleSlots(first, last);
    }

    private GuiPager pager() {
        return GuiPager.of(page, kits() == null ? 9 : kits().maxKits(), GuiLayouts.DASH_SLOTS_PER_PAGE);
    }

    @Override
    protected String title() {
        GuiPager pager = pager();
        return GuiMenuFactory.mainMenuTitle(kits(), player, pager.currentPage(), pager.maxPages());
    }

    @Override
    protected void renderContent(StaticPane content) {
        GuiPager pager = pager();
        page = pager.currentPage();

        VisibleSlots visible = visibleSlots(page, kits() == null ? 9 : kits().maxKits());
        int first = visible.first();
        int last = visible.last();
        int shown = last - first + 1;
        int offset = (GuiLayouts.COLUMNS - shown) / 2; // a short last page stays centered
        KitManager kitsMgr = kits().kitManager();
        UUID id = player.getUniqueId();

        // One loop for both rows: kits on row 0, ender chests on row 1.
        for (KitKind kind : KitKind.values()) {
            int row = kind == KitKind.KIT ? GuiLayouts.DASH_KITS_ROW : GuiLayouts.DASH_ENDERCHESTS_ROW;
            for (int i = 0; i < shown; i++) {
                int slot = first + i;
                ItemStack[] data = kitsMgr.peek(kind, id, slot); // read-only view, no cloning per redraw
                boolean has = data != null;
                String name = kind.label() + " " + slot;
                ItemStack card = has
                        ? GuiCard.kitCard(iconOf(kind, data), name, data, GuiCard.CardState.READY)
                        : GuiCard.kitCard(kind == KitKind.KIT ? Material.GRAY_DYE : Material.ENDER_EYE,
                                name, null, GuiCard.CardState.EMPTY);
                content.addItem(wrap(card, (p, e) -> cardClicked(p, e.getClick().isRightClick(), kind, slot, has), player),
                        offset + i, row);
            }
        }

        List<GuiItem> actions = new ArrayList<>();
        actions.add(wrap(GuiTheme.heading(Material.NETHER_STAR, "Kit Room",
                GuiTheme.dim("Browse every item"), GuiTheme.hint("Click", "open")), (p, e) -> {
            SoundManager.playClick(p);
            gui.OpenKitRoom(p);
            kits().broadcastManager().broadcastPlayerOpenedKitRoom(p);
        }, player));
        actions.add(wrap(GuiTheme.heading(Material.BOOKSHELF, "Premade Kits",
                GuiTheme.dim("Server kit presets"), GuiTheme.hint("Click", "browse")), (p, e) -> {
            SoundManager.playClick(p);
            gui.OpenPublicKitMenu(p);
        }, player));
        actions.add(wrap(GuiTheme.named(Material.ANVIL, GuiTheme.TEXT + "<bold>", "Repair All",
                GuiTheme.dim("Restore durability"), GuiTheme.hint("Click", "repair")), (p, e) -> {
            if (!ActionGuards.allowed(p, "perplayerkit.repair") || !ActionGuards.dataReady(p)) return;
            SoundManager.playClick(p);
            kits().broadcastManager().broadcastPlayerRepaired(p);
            PlayerUtil.repairAll(p);
            p.updateInventory();
            SoundManager.playSuccess(p);
        }, player));
        if (player.hasPermission("perplayerkit.admin") || player.hasPermission("perplayerkit.editkitroom")) {
            actions.add(wrap(GuiTheme.named(Material.COMMAND_BLOCK, GuiTheme.ACCENT + "<bold>", "Staff Tools",
                    GuiTheme.dim("Kits and settings"), GuiTheme.hint("Click", "open")), (p, e) -> {
                SoundManager.playClick(p);
                gui.OpenMainManagerMenu(p);
            }, player));
        }
        GuiLayouts.centerItems(content, actions, GuiLayouts.DASH_ACTIONS_ROW);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this)
                .pager(pager(), target -> {
                    page = target;
                    GUI.rememberMainMenuPage(player, page);
                    refresh();
                })
                .action(GuiLayouts.DOCK_ACTION_COL_B, GuiTheme.dangerTool(Material.LAVA_BUCKET, "Clear Inventory",
                        "Requires shift-click", "Shift-click", "clear"), (p, e) -> {
                    SoundManager.playClick(p);
                    if (e.getClick().isShiftClick()) {
                        p.getInventory().clear();
                        kits().lang().send(p, "success.inventory-cleared");
                        SoundManager.playSuccess(p);
                    }
                })
                .applyTo(chrome);
    }

    private void cardClicked(Player p, boolean edit, KitKind kind, int slot, boolean has) {
        if (!ActionGuards.allowed(p, MENU, LocationFeature.MENU) || !ActionGuards.dataReady(p)) return;
        SoundManager.playClick(p);

        if (edit || !has) {
            if (kind == KitKind.KIT) gui.openKitEditor(p, slot);
            else gui.openEnderchestEditor(p, slot);
            return;
        }
        if (!ActionGuards.allowed(p, kind.permission())) return;
        kits().kitManager().load(kind, p, slot);
        p.closeInventory();
    }

    private static Material iconOf(KitKind kind, ItemStack[] data) {
        return kind == KitKind.KIT ? representativeItem(data) : Material.ENDER_CHEST;
    }

    private static Material representativeItem(ItemStack[] kit) {
        if (kit == null) return Material.CHEST;
        int[] order = {38, 39, 37, 36};
        for (int index : order) {
            if (index < kit.length && kit[index] != null && !kit[index].getType().isAir()) {
                return kit[index].getType();
            }
        }
        for (ItemStack item : kit) {
            if (item != null && !item.getType().isAir()) return item.getType();
        }
        return Material.CHEST;
    }
}
