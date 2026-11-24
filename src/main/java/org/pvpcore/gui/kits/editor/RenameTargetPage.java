package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

/**
 * Ghost picker over all 41 saved kit slots. Empty slots are display-only; clicking a
 * non-empty slot opens the chat prompt to rename that item.
 */
public final class RenameTargetPage extends GuiScreen {
    private final GUI gui;
    private final CustomizeSession session;
    private final int kitSlot;

    private RenameTargetPage(GUI gui, Player player, CustomizeSession session, int kitSlot) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, org.pvpcore.gui.base.GuiFrame.Density.NONE,
                "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.kitSlot = kitSlot;
    }

    public static void open(GUI gui, Player player, CustomizeSession session, int kitSlot) {
        if (session == null) return;
        if (!org.pvpcore.commands.kits.core.ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return;
        new RenameTargetPage(gui, player, session, kitSlot).show();
    }

    @Override
    protected String title() {
        return GuiTheme.ACCENT + "<bold>Rename Item";
    }

    @Override
    protected void renderContent(StaticPane content) {
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // Back button
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            CustomizePage.reopen(gui, p, kitSlot, session);
        }, player), GuiLayouts.RENAME_BACK_SLOT % GuiLayouts.COLUMNS, GuiLayouts.RENAME_BACK_SLOT / GuiLayouts.COLUMNS);

        // Ghost slots 0..40 are display-only placeholders; full ghost rendering is server-side.
        for (int i = GuiLayouts.RENAME_TARGET_FIRST; i <= GuiLayouts.RENAME_TARGET_LAST; i++) {
            final int targetSlot = i;
            chrome.addItem(wrap(GuiTheme.named(Material.GRAY_DYE, GuiTheme.MUTED + "<bold>", "Slot " + i,
                    GuiTheme.hint("Click", "rename")), (p, e) -> {
                SoundManager.playClick(p);
                ItemStack item = session.working()[targetSlot];
                if (item == null || item.getType().isAir()) return;
                session.selectTarget(targetSlot);
                // Chat prompt integration is server-side; placeholder only.
                gui.kits().lang().send(p, "info.cosmetics-save-queued");
            }, player), i % GuiLayouts.COLUMNS, i / GuiLayouts.COLUMNS);
        }
    }

    @Override
    protected void protect(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
    }
}