package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.kits.page.KitRoomPageSettingsPage;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;

/**
 * The framed kit-room item editor. Replaces {@code AdminGui.openKitRoomItemEditor}.
 *
 * <p>Layout (6 rows): rows 0-4 are the 45 item slots of one kit-room page, row 5
 * is the footer with Back (discards) and Save. There is no frame: every slot of
 * rows 0-4 belongs to the page.</p>
 */
public final class KitRoomEditorPage extends GuiScreen {
    private final GUI gui;
    private static final String PERMISSION = "perplayerkit.editkitroom";

    private final int pageIndex;

    public KitRoomEditorPage(GUI gui, Player player, int pageIndex) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, GuiFrame.Density.NONE, PERMISSION, null);
        this.gui = gui;
        this.pageIndex = pageIndex;
    }

    public static void open(GUI gui, Player player, int pageIndex) {
        if (!ActionGuards.allowed(player, PERMISSION)) return;
        new KitRoomEditorPage(gui, player, pageIndex).show();
    }

    @Override
    protected String title() {
        return GuiMenuFactory.kitRoomTitle(kits(), "Editing: " + kits().kitRoomDataManager().getPageName(pageIndex), true);
    }

    @Override
    protected void renderContent(StaticPane content) {
        // The items live in the inventory, not in a pane. See afterShow.
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        int footerRow = editorFooterRow();
        chrome.addItem(wrap(GuiTheme.named(Material.ARROW, GuiTheme.MUTED, "\u25C0 Back", GuiTheme.dim("Discards your changes")), (p, e) -> {
            SoundManager.playClick(p);
            new KitRoomPageSettingsPage(gui, p, pageIndex).show();
        }, player), 0, footerRow);

        chrome.addItem(wrap(GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Save page", GuiTheme.dim("Writes these items"), GuiTheme.hint("Click", "save")), (p, e) -> {
            Inventory top = p.getOpenInventory().getTopInventory();
            ItemStack[] data = new ItemStack[GuiLayouts.KIT_ROOM_CONTENT_END];
            for (int i = 0; i < GuiLayouts.KIT_ROOM_CONTENT_END; i++) {
                ItemStack item = top.getItem(i);
                data[i] = item == null ? null : item.clone();
            }
            kits().kitRoomDataManager().setKitRoom(pageIndex, data);
            kits().kitRoomDataManager().savePagesToDBAsync(List.of(pageIndex));
            SoundManager.playSuccess(p);
            new KitRoomPageSettingsPage(gui, p, pageIndex).show();
        }, player), GuiLayouts.FOOTER_SAVE_COL, footerRow);

        for (int col = 1; col < GuiLayouts.COLUMNS; col++) {
            if (col == GuiLayouts.FOOTER_SAVE_COL) continue;
            chrome.addItem(new GuiItem(GuiTheme.filler(kits())), col, footerRow);
        }
    }

    @Override
    protected void protect(ChestGui chest) {
        GuiGuard.editable(chest, PERMISSION, null,
                slot -> slot >= 0 && slot < GuiLayouts.KIT_ROOM_CONTENT_END);
    }

    // refresh() must not repaint here: it would discard the player's in-progress edits.
    @Override
    protected void afterInventory(Inventory inventory, boolean refresh) {
        if (refresh) {
            return;
        }
        ItemStack[] contents = kits().kitRoomDataManager().getKitRoomPage(pageIndex);
        if (contents == null) return;
        for (int i = 0; i < GuiLayouts.KIT_ROOM_CONTENT_END && i < contents.length; i++) {
            inventory.setItem(i, contents[i]);
        }
    }
}
