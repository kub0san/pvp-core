package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.ItemFilter;
import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

/**
 * The kit room: pick up any item, page by page. A full-canvas screen with no
 * frame and no chrome panes for the items -- the items are real items written
 * straight into the inventory, so the player can take them.
 *
 *   slots 0-44   the items of the page (take anything)
 *   slot  45     back
 *   slot  46     previous page
 *   slots 47-51  page tabs (the page you are on glows)
 *   slot  52     next page
 *   slot  53     refill this page
 *
 * Changing page redraws this same inventory and only updates its title, so the
 * cursor stays where it is.
 */
public final class KitRoomPage extends GuiScreen {
    private final GUI gui;
    private static final String PERMISSION = "perplayerkit.menu";

    private static final int BACK_SLOT = GuiLayouts.FOOTER_START; // 45
    private static final int PREV_SLOT = 46;
    private static final int FIRST_TAB_SLOT = 47;
    private static final int TAB_COUNT = 5;
    private static final int NEXT_SLOT = 52;
    private static final int REFILL_SLOT = 53;

    private final View view;

    private KitRoomPage(GUI gui, Player player, int page) {
        super(gui, player, GuiLayouts.MENU_SIZE / GuiLayouts.COLUMNS, GuiFrame.Density.NONE,
                PERMISSION, LocationFeature.KIT_ROOM);
        this.gui = gui;
        this.view = new View(clampPage(page, gui.kits().kitRoomDataManager().getPageCount()));
    }

    public static void open(GUI gui, Player player, int page) {
        if (!(ActionGuards.allowed(player, PERMISSION, LocationFeature.KIT_ROOM) && ActionGuards.dataReady(player))) {
            return;
        }
        new KitRoomPage(gui, player, page).show();
    }

    private static int clampPage(int page, int count) {
        return Math.max(0, Math.min(page, Math.max(0, count - 1)));
    }

    @Override
    protected String title() {
        return GuiMenuFactory.kitRoomTitle(kits(), kits().kitRoomDataManager().getPageTitle(view.current), true);
    }

    @Override
    protected void renderContent(StaticPane content) {
        // The items are real inventory items; they are written in paintItems(),
        // not painted as pane items.
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        KitRoomDataManager room = kits().kitRoomDataManager();
        int count = room.getPageCount();
        int current = view.current;
        boolean staff = player.hasPermission("perplayerkit.editkitroom");

        boolean hasPrev = current > 0;
        boolean hasNext = current < count - 1;
        int first = view.firstTab(count);

        // Occupancy: back + refill always, prev/next/tabs only when they exist.
        boolean[] occupied = new boolean[GuiLayouts.COLUMNS];
        occupied[BACK_SLOT - GuiLayouts.FOOTER_START] = true;
        if (hasPrev) occupied[PREV_SLOT - GuiLayouts.FOOTER_START] = true;
        for (int i = 0; i < TAB_COUNT; i++) {
            if (first + i < count) occupied[FIRST_TAB_SLOT - GuiLayouts.FOOTER_START + i] = true;
        }
        if (hasNext) occupied[NEXT_SLOT - GuiLayouts.FOOTER_START] = true;
        occupied[REFILL_SLOT - GuiLayouts.FOOTER_START] = true;

        // Filler everywhere the footer has no button.
        for (int i = 0; i < GuiLayouts.COLUMNS; i++) {
            if (!occupied[i]) {
                chrome.addItem(new GuiItem(GuiTheme.filler(kits())), i, editorFooterRow());
            }
        }

        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            gui.OpenMainMenu(p, GUI.lastMainMenuPageOf(p));
        }, player), BACK_SLOT - GuiLayouts.FOOTER_START, editorFooterRow());

        if (hasPrev) {
            chrome.addItem(wrap(GuiTheme.arrow(false, current - 1, count), (p, e) -> {
                SoundManager.playClick(p);
                showPage(current - 1);
            }, player), PREV_SLOT - GuiLayouts.FOOTER_START, editorFooterRow());
        }

        for (int i = 0; i < TAB_COUNT; i++) {
            int pageIndex = first + i;
            if (pageIndex >= count) {
                continue;
            }
            ItemStack tab = pageTab(room, pageIndex, current, staff);
            if (pageIndex == current) {
                tab = GuiTheme.glow(tab);
            }
            int slot = FIRST_TAB_SLOT + i;
            chrome.addItem(wrap(tab, (p, e) -> {
                SoundManager.playClick(p);
                if (e.isShiftClick() && p.hasPermission("perplayerkit.editkitroom")) {
                    gui.OpenKitRoomPageSettings(p, pageIndex);
                } else {
                    showPage(pageIndex);
                }
            }, player), slot - GuiLayouts.FOOTER_START, editorFooterRow());
        }

        if (hasNext) {
            chrome.addItem(wrap(GuiTheme.arrow(true, current + 1, count), (p, e) -> {
                SoundManager.playClick(p);
                showPage(current + 1);
            }, player), NEXT_SLOT - GuiLayouts.FOOTER_START, editorFooterRow());
        }

        chrome.addItem(wrap(GuiTheme.named(Material.BEACON, GuiTheme.GOLD + "<bold>", "Refill page", GuiTheme.dim("Restock every item"), GuiTheme.hint("Click", "refill")), (p, e) -> {
            SoundManager.playClick(p);
            showPage(view.current);
        }, player), REFILL_SLOT - GuiLayouts.FOOTER_START, editorFooterRow());
    }

    private static ItemStack pageTab(KitRoomDataManager room, int index, int current, boolean staff) {
        boolean here = index == current;
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(here ? GuiTheme.status(GuiTheme.GOLD, true, "Viewing") : GuiTheme.dim("Page " + (index + 1)));
        lore.add("");
        if (!here) lore.add(GuiTheme.hint("Click", "open"));
        if (staff) lore.add(GuiTheme.hint("Shift-click", "edit page"));
        return GuiTheme.named(room.getPageIcon(index), (here ? GuiTheme.GOLD : GuiTheme.TEXT) + "<bold>",
                room.getPageName(index), lore.toArray(new String[0]));
    }

    @Override
    protected void afterInventory(Inventory inventory, boolean refresh) {
        paintItems(inventory);
    }

    @Override
    protected void protect(ChestGui chest) {
        GuiGuard.takeOnly(chest, PERMISSION, LocationFeature.KIT_ROOM,
                slot -> slot >= 0 && slot < GuiLayouts.FOOTER_START);
    }

    private void paintItems(Inventory inventory) {
        KitRoomDataManager room = kits().kitRoomDataManager();
        int count = room.getPageCount();
        view.current = clampPage(view.current, count);

        ItemStack[] contents = kits().itemFilter().filterItemStack(room.getKitRoomPage(view.current));
        for (int i = 0; i < GuiLayouts.FOOTER_START; i++) {
            inventory.setItem(i, contents == null || i >= contents.length ? null : contents[i]);
        }
    }

    /** Changes the page and redraws this screen in place, keeping the cursor. */
    private void showPage(int page) {
        view.current = clampPage(page, kits().kitRoomDataManager().getPageCount());
        refresh();
    }

    /** State of one open kit room. The screen instance is per-open, so this is per-viewer. */
    private final class View {
        private int current;

        private View(int current) {
            this.current = current;
        }

        private int firstTab(int count) {
            int first = Math.max(0, current - 2);
            if (first + TAB_COUNT > count) {
                first = Math.max(0, count - TAB_COUNT);
            }
            return first;
        }
    }
}
