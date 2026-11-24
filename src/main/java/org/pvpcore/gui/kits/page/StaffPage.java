package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Staff tools on a large bordered canvas: top glass, centered tools, dock with Back.
 */
public final class StaffPage extends GuiScreen {
    private static final String EDIT = "perplayerkit.editkitroom";
    private static final String ADMIN = "perplayerkit.admin";
    private final GUI gui;

    private StaffPage(GUI gui, Player player) {
        super(gui, player, GuiLayouts.ROWS_DOCK_TOOLS, GuiFrame.Density.BORDER, null, null);
        this.gui = gui;
    }

    public static void open(GUI gui, Player player) {
        if (!player.hasPermission(EDIT) && !player.hasPermission(ADMIN)) return;
        new StaffPage(gui, player).show();
    }

    @Override
    protected String title() {
        return GuiMenuFactory.sectionTitle(kits(), "Staff <dark_gray>\u00BB <white>Tools");
    }

    @Override
    protected void renderContent(StaticPane content) {
        List<GuiItem> tools = new ArrayList<>();
        if (player.hasPermission(EDIT)) {
            tools.add(wrap(GuiTheme.tool(Material.CHEST, "Kit Room Pages", "Add, rename and fill pages", "Click", "open"), (p, e) -> {
                SoundManager.playClick(p);
                KitRoomPagesPage.open(gui, p);
            }, player));
            tools.add(wrap(GuiTheme.tool(Material.BOOKSHELF, "Premade Kits", "Server kits and categories", "Click", "open"), (p, e) -> {
                SoundManager.playClick(p);
                gui.OpenPublicKitMenu(p);
            }, player));
        }
        if (player.hasPermission(ADMIN)) {
            tools.add(wrap(GuiTheme.tool(Material.COMMAND_BLOCK, "Plugin Settings", "Edit kits config in-game", "Click", "open"), (p, e) -> {
                SoundManager.playClick(p);
                SettingsHubPage.open(gui, p);
            }, player));
            tools.add(wrap(GuiTheme.tool(Material.REPEATER, "Reload", "Configs and messages", "Click", "reload"), (p, e) -> {
                SoundManager.playClick(p);
                p.closeInventory();
                p.performCommand("pvpcore reload");
            }, player));
        }
        GuiLayouts.centerItems(content, tools, GuiLayouts.DOCK_TOOLS_ROW);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this).back((p, e) -> gui.OpenMainMenu(p, GUI.lastMainMenuPageOf(p))).applyTo(chrome);
    }
}
