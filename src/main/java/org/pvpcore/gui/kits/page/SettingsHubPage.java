package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings hub. Bordered: groups on the two content rows (18 per page), dock below
 * with Back, pager and, while changes are staged, Discard and Save.
 */
public final class SettingsHubPage extends GuiScreen {
    private final GUI gui;
    private int page;

    public SettingsHubPage(GUI gui, Player player, int page) {
        super(gui, player, GuiLayouts.ROWS_DOCK_LIST18, GuiFrame.Density.BORDER, "perplayerkit.admin", null);
        this.gui = gui;
        this.page = Math.max(0, page);
    }

    public static void open(GUI gui, Player player) {
        if (!player.hasPermission("perplayerkit.admin")) return;
        new SettingsHubPage(gui, player, 0).show();
    }

    public int page() {
        return page;
    }

    String groupName() {
        return null;
    }

    private GuiPager pager() {
        return GuiPager.of(page, GuiSettingsSchema.groups().size(), GuiSettingsSchema.GROUPS_PER_SCREEN);
    }

    @Override
    protected String title() {
        int staged = GuiSettingsSupport.stagedOf(player).size();
        return GuiMenuFactory.sectionTitle(kits(), "Settings" + (staged > 0 ? " <dark_gray>\u00BB <gold>" + staged + " unsaved" : ""));
    }

    @Override
    protected void renderContent(StaticPane content) {
        GuiPager pager = pager();
        page = pager.currentPage();
        List<GuiSettingsSchema.Group> visible = pager.slice(GuiSettingsSchema.groups());
        for (int i = 0; i < visible.size(); i++) {
            GuiSettingsSchema.Group group = visible.get(i);
            content.addItem(wrap(groupItem(group), (p, e) -> {
                SoundManager.playClick(p);
                new SettingsGroupPage(gui, p, group.name(), 0).show();
            }, player), i % GuiLayouts.COLUMNS, GuiLayouts.contentRow(i / GuiLayouts.COLUMNS));
        }
    }

    private ItemStack groupItem(GuiSettingsSchema.Group group) {
        List<String> lore = new ArrayList<>();
        lore.add(GuiTheme.dim(group.defs().size() + " settings"));
        String marker = GuiSettingsSchema.groupMarker(group);
        if (marker != null) lore.add(GuiTheme.dim(marker));
        long staged = group.defs().stream().filter(d -> GuiSettingsSupport.stagedOf(player).containsKey(d.path())).count();
        if (staged > 0) lore.add(GuiTheme.GOLD + staged + " unsaved");
        lore.add(GuiTheme.hint("Click", "open"));
        return GuiTheme.named(group.icon(), (staged > 0 ? GuiTheme.GOLD : GuiTheme.TEXT) + "<bold>", group.name(),
                lore.toArray(new String[0]));
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock dock = GuiDock.of(this)
                .back((p, e) -> gui.OpenMainManagerMenu(p))
                .pager(pager(), target -> {
                    page = target;
                    refresh();
                });
        addSaveDiscard(dock, this);
        dock.applyTo(chrome);
    }

    /** Shared by the hub and the group screen: Discard + Save appear only while something is staged. */
    static void addSaveDiscard(GuiDock dock, GuiScreen screen) {
        Player player = screen.player();
        if (GuiSettingsSupport.stagedOf(player).isEmpty()) return;
        dock.action(GuiLayouts.DOCK_ACTION_COL_A, GuiTheme.named(Material.RED_DYE, GuiTheme.RED + "<bold>", "Discard",
                GuiTheme.dim("Undo staged changes"), GuiTheme.hint("Click", "discard")),
                (p, e) -> GuiSettingsSupport.discard(screen, p));
        dock.action(GuiLayouts.DOCK_ACTION_COL_B, GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Save",
                GuiTheme.dim("Write to config"), GuiTheme.hint("Click", "save")),
                (p, e) -> GuiSettingsSupport.save(screen, p));
    }
}
