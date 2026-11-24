package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.entity.Player;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;

/**
 * One settings group. Bordered: 27 settings on three content rows, dock below
 * (Back, pager, Discard/Save while changes are staged).
 */
public final class SettingsGroupPage extends GuiScreen {
    private final GUI gui;
    private final String groupName;
    public int page;

    public SettingsGroupPage(GUI gui, Player player, String groupName, int page) {
        super(gui, player, GuiLayouts.ROWS_DOCK_LIST27, GuiFrame.Density.BORDER, "perplayerkit.admin", null);
        this.gui = gui;
        this.groupName = groupName;
        this.page = Math.max(0, page);
    }

    public String groupName() {
        return groupName;
    }

    public int page() {
        return page;
    }

    private GuiSettingsSchema.Group group() {
        for (GuiSettingsSchema.Group g : GuiSettingsSchema.groups()) {
            if (g.name().equals(groupName)) return g;
        }
        return null;
    }

    private GuiPager pager() {
        GuiSettingsSchema.Group g = group();
        return GuiPager.of(page, g == null ? 0 : g.defs().size(), GuiSettingsSchema.SETTINGS_PER_SCREEN);
    }

    @Override
    protected String title() {
        return GuiMenuFactory.sectionTitle(kits(), "Settings <dark_gray>\u00BB <white>" + groupName);
    }

    @Override
    protected void renderContent(StaticPane content) {
        GuiSettingsSchema.Group g = group();
        GuiPager pager = pager();
        page = pager.currentPage();
        if (g == null) return;
        List<GuiSettingsSchema.Def> visible = pager.slice(g.defs());
        for (int i = 0; i < visible.size(); i++) {
            GuiSettingsSchema.Def def = visible.get(i);
            content.addItem(wrap(GuiSettingsSupport.settingItem(kits(), player, def),
                            (p, e) -> GuiSettingsSupport.settingClicked(this, p, def, e), player),
                    i % GuiLayouts.COLUMNS, GuiLayouts.contentRow(i / GuiLayouts.COLUMNS));
        }
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock dock = GuiDock.of(this)
                .back((p, e) -> new SettingsHubPage(gui, p, 0).show())
                .pager(pager(), target -> {
                    page = target;
                    refresh();
                });
        SettingsHubPage.addSaveDiscard(dock, this);
        dock.applyTo(chrome);
    }
}
