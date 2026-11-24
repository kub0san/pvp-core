package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiSessions;
import org.pvpcore.util.kits.SoundManager;

/**
 * The view-only look into the player's own ender chest (the /enderchest command).
 * Three rows: the 27 ender chest slots, nothing else. The items are real items
 * written straight into the inventory, so this screen is a full canvas with no
 * frame and no chrome. Never calls {@code refresh()}, so the ender chest items
 * are simply written on every paint.
 */
public final class EnderchestViewPage extends GuiScreen {
    private static final int ROWS = 3;

    private EnderchestViewPage(Player player) {
        super(GuiSessions.currentOr(JavaPlugin.getProvidingPlugin(GuiScreen.class)),
                player, ROWS, GuiFrame.Density.NONE, null, null);
    }

    /** Opens the view. The caller already checked where the player may use it. */
    public static void open(Player player) {
        new EnderchestViewPage(player).show();
        SoundManager.playOpenGui(player);
    }

    @Override
    protected String title() {
        if (kits() != null) return GuiMenuFactory.langTitle(kits(), "gui.enderchest-view-title");
        return "Ender Chest";
    }

    @Override
    protected void renderContent(StaticPane content) {
        // Empty on purpose: the 27 ender chest slots are written straight into
        // the inventory in afterShow().
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // No chrome: the ender chest items are the whole screen.
    }

    @Override
    protected void afterInventory(Inventory inventory, boolean refresh) {
        paintItems(inventory);
    }

    private void paintItems(Inventory inventory) {
        ItemStack[] items = player.getEnderChest().getContents();
        for (int i = 0; i < 27 && i < items.length; i++) {
            inventory.setItem(i, items[i]);
        }
    }

    @Override
    protected void protect(ChestGui chest) {
        // No permission to check here: the caller already checked where the player may use it.
        GuiGuard.readOnly(chest, null, null, rows());
    }
}
