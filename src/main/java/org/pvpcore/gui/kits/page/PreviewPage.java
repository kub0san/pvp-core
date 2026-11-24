package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

/**
 * The framed premade kit preview. Replaces {@code PublicKitGui}: a 5-row full-canvas
 * screen (no frame) that lays the kit out like an inventory with Back and Load
 * actions at the bottom.
 */
public final class PreviewPage extends GuiScreen {
    private final GUI gui;
    private static final String PERMISSION = "perplayerkit.publickit";

    private final String kitId;
    private final ItemStack[] kit;

    public PreviewPage(GUI gui, Player player, String kitId, ItemStack[] kit) {
        super(gui, player, GuiLayouts.ROWS_PREVIEW, GuiFrame.Density.NONE, PERMISSION, LocationFeature.PUBLIC_KITS);
        this.gui = gui;
        this.kitId = kitId;
        this.kit = kit;
    }

    @Override
    protected String title() {
        return GuiMenuFactory.viewPublicKitTitle(kits(), kitId);
    }

    @Override
    protected void renderContent(StaticPane content) {
        // Kit items are placed directly in the inventory after show to avoid redraw issues.
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        int back = GuiLayouts.PREVIEW_BACK_SLOT;
        int load = GuiLayouts.PREVIEW_LOAD_SLOT;

        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            gui.OpenPublicKitMenu(p);
        }, player), back % GuiLayouts.COLUMNS, back / GuiLayouts.COLUMNS);

        chrome.addItem(wrap(GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Load kit", GuiTheme.dim("Equip it now"), GuiTheme.hint("Click", "load")), (p, e) -> {
            if (!ActionGuards.allowed(p, PERMISSION) || !ActionGuards.dataReady(p)) return;
            SoundManager.playClick(p);
            kits().kitManager().loadPublicKit(p, kitId);
            p.closeInventory();
        }, player), load % GuiLayouts.COLUMNS, load / GuiLayouts.COLUMNS);

        // Only the two genuinely free cells of row 4 may hold filler. Slots 36-40
        // belong to the kit's armor, which is written straight into the
        // inventory in afterShow(); painting them here would wipe the armor on
        // the next redraw.
        for (int col = GuiLayouts.PREVIEW_FILLER_FIRST_COL; col <= GuiLayouts.PREVIEW_FILLER_LAST_COL; col++) {
            chrome.addItem(new GuiItem(GuiTheme.filler(kits())), col, GuiLayouts.ROWS_PREVIEW - 1);
        }
    }

    @Override
    protected void afterInventory(Inventory inv, boolean refresh) {
        paintItems(inv);
    }

    private void paintItems(Inventory inv) {
        for (int i = 9; i < 36; i++) {
            inv.setItem(i - 9, at(kit, i));      // inventory rows
        }
        for (int i = 0; i < 9; i++) {
            inv.setItem(27 + i, at(kit, i));     // hotbar
        }
        for (int i = 0; i < 5; i++) {
            inv.setItem(36 + i, at(kit, 36 + i)); // boots, leggings, chestplate, helmet, offhand
        }
    }

    private static ItemStack at(ItemStack[] kit, int index) {
        return kit != null && index < kit.length ? kit[index] : null;
    }
}