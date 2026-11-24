package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pvpcore.features.kits.cosmetics.BannerDesign;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

/**
 * Pattern/dye picker for one banner layer. Returns a {@link BannerDesign.Layer} to the designer.
 */
public final class BannerPatternPickerPage extends GuiScreen {
    private final GUI gui;
    private final CustomizeSession session;
    private final BannerDesign design;
    private final int layerIndex;

    private BannerPatternPickerPage(GUI gui, Player player, BannerDesign design, int layerIndex, CustomizeSession session) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, org.pvpcore.gui.base.GuiFrame.Density.NONE,
                "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.design = design;
        this.layerIndex = layerIndex;
        this.session = session;
    }

    public static void open(GUI gui, Player player, BannerDesign design, int layerIndex, CustomizeSession session) {
        if (design == null || session == null) return;
        if (!org.pvpcore.commands.kits.core.ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return;
        new BannerPatternPickerPage(gui, player, design, layerIndex, session).show();
    }

    @Override
    protected String title() {
        return GuiTheme.ACCENT + "<bold>Pick Pattern";
    }

    @Override
    protected void renderContent(StaticPane content) {
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            BannerDesignerPage.open(gui, p, session);
        }, player), GuiLayouts.BANNER_BACK_SLOT % GuiLayouts.COLUMNS, GuiLayouts.BANNER_BACK_SLOT / GuiLayouts.COLUMNS);
    }

    @Override
    protected void protect(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
    }
}