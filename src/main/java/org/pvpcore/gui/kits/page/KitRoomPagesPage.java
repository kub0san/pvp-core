package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.SoundManager;

/**
 * Staff: the kit-room page list. Bordered: 18 pages on two content rows, dock with
 * back, pager, and "Add page".
 */
public final class KitRoomPagesPage extends GuiScreen {
    private static final String PERMISSION = "perplayerkit.editkitroom";
    private final GUI gui;
    private int page;

    public KitRoomPagesPage(GUI gui, Player player, int page) {
        super(gui, player, GuiLayouts.ROWS_DOCK_LIST18, GuiFrame.Density.BORDER, PERMISSION, null);
        this.gui = gui;
        this.page = Math.max(0, page);
    }

    public static void open(GUI gui, Player player) {
        if (!player.hasPermission(PERMISSION)) return;
        new KitRoomPagesPage(gui, player, 0).show();
    }

    private GuiPager pager() {
        return GuiPager.of(page, kits().kitRoomDataManager().getPageCount(), GuiLayouts.KIT_ROOM_PAGES_PER_SCREEN);
    }

    @Override
    protected String title() {
        return GuiMenuFactory.sectionTitle(kits(), "Kit Room <dark_gray>\u00BB <white>Pages");
    }

    @Override
    protected void renderContent(StaticPane content) {
        KitRoomDataManager room = kits().kitRoomDataManager();
        GuiPager pager = pager();
        page = pager.currentPage();
        int from = pager.from();
        int to = pager.to(room.getPageCount());
        for (int index = from; index < to; index++) {
            int i = index - from;
            int shown = index;
            content.addItem(wrap(GuiTheme.named(room.getPageIcon(index), GuiTheme.TEXT + "<bold>", room.getPageName(index),
                            GuiTheme.dim("Page " + (index + 1)), GuiTheme.hint("Click", "manage")),
                    (p, e) -> {
                        SoundManager.playClick(p);
                        new KitRoomPageSettingsPage(gui, p, shown).show();
                    }, player), i % GuiLayouts.COLUMNS, GuiLayouts.contentRow(i / GuiLayouts.COLUMNS));
        }
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this)
                .back((p, e) -> gui.OpenMainManagerMenu(p))
                .pager(pager(), target -> {
                    page = target;
                    refresh();
                })
                .action(GuiLayouts.DOCK_ACTION_COL_B, GuiTheme.named(Material.WRITABLE_BOOK, GuiTheme.GREEN + "<bold>",
                        "+ Add page", GuiTheme.dim("Append an empty page"), GuiTheme.hint("Click", "add")), (p, e) -> {
                    SoundManager.playClick(p);
                    kits().kitRoomDataManager().addPage();
                    kits().kitRoomDataManager().saveToDBAsync();
                    page = pager().maxPages() - 1;
                    refresh();
                })
                .applyTo(chrome);
    }
}
