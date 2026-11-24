package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;

/** Staff: settings for one kit-room page. Large bordered canvas with tools centered. */
public final class KitRoomPageSettingsPage extends GuiScreen {
    private static final String PERMISSION = "perplayerkit.editkitroom";
    private final GUI gui;
    private final int pageIndex;

    public KitRoomPageSettingsPage(GUI gui, Player player, int pageIndex) {
        super(gui, player, GuiLayouts.ROWS_DOCK_TOOLS, GuiFrame.Density.BORDER, PERMISSION, null);
        this.gui = gui;
        this.pageIndex = pageIndex;
    }

    @Override
    protected String title() {
        return GuiMenuFactory.sectionTitle(kits(), "Kit Room <dark_gray>\u00BB <white>"
                + kits().kitRoomDataManager().getPageName(pageIndex));
    }

    private void reopen(Player p) {
        new KitRoomPageSettingsPage(gui, p, pageIndex).show();
    }

    @Override
    protected void renderContent(StaticPane content) {
        KitRoomDataManager room = kits().kitRoomDataManager();

        GuiItem edit = wrap(GuiTheme.tool(Material.CHEST, "Edit items", "Opens the item editor", "Click", "open"), (p, e) -> {
            SoundManager.playClick(p);
            gui.OpenKitRoomItemEditor(p, pageIndex);
        }, player);

        GuiItem rename = wrap(GuiTheme.tool(Material.OAK_SIGN, "Rename", "Now: " + room.getPageName(pageIndex),
                "Click", "type a name"), (p, e) -> {
            SoundManager.playClick(p);
            GuiChatPrompt.ask(gui.plugin(), p, "New name for this page:", text -> {
                kits().kitRoomDataManager().setPageSetting(pageIndex, false, text);
                kits().kitRoomDataManager().saveToDBAsync();
                GuiChatPrompt.say(p, GuiTheme.ok("Name updated"));
                reopen(p);
            }, () -> reopen(p));
        }, player);

        GuiItem title = wrap(GuiTheme.tool(Material.NAME_TAG, "Title", "Now: " + room.getPageTitle(pageIndex),
                "Click", "type a title"), (p, e) -> {
            SoundManager.playClick(p);
            GuiChatPrompt.ask(gui.plugin(), p, "New title for this page:", text -> {
                kits().kitRoomDataManager().setPageSetting(pageIndex, true, text);
                kits().kitRoomDataManager().saveToDBAsync();
                GuiChatPrompt.say(p, GuiTheme.ok("Title updated"));
                reopen(p);
            }, () -> reopen(p));
        }, player);

        GuiItem icon = wrap(GuiTheme.tool(room.getPageIcon(pageIndex), "Icon", "The tab icon in the kit room",
                "Click with an item", "use it"), (p, e) -> {
            Material chosen = GuiTheme.cursorMaterial(e);
            if (chosen == null) {
                GuiChatPrompt.say(p, GuiTheme.warn("Pick up an item first, then click Icon."));
                return;
            }
            kits().kitRoomDataManager().setPageIcon(pageIndex, chosen);
            kits().kitRoomDataManager().saveMeta();
            SoundManager.playSuccess(p);
            refresh();
        }, player);

        GuiItem delete = wrap(GuiTheme.dangerTool(Material.LAVA_BUCKET, "Delete page", "Removes the page and its items",
                "Click", "delete"), (p, e) -> {
            SoundManager.playClick(p);
            GuiConfirm.open(p, "Delete this page?", new String[]{"The page and its items are removed."}, true, PERMISSION,
                    () -> {
                        kits().kitRoomDataManager().removePage(pageIndex);
                        kits().kitRoomDataManager().saveToDBAsync();
                        KitRoomPagesPage.open(gui, p);
                    },
                    () -> reopen(p));
        }, player);

        GuiLayouts.centerItems(content, List.of(edit, rename, title, icon, delete), GuiLayouts.DOCK_TOOLS_ROW);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this).back((p, e) -> KitRoomPagesPage.open(gui, p)).applyTo(chrome);
    }
}
