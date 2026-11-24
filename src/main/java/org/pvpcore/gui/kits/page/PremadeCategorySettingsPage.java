package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.kits.data.PremadeStore;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;

/** Staff: settings for one premade category. Large bordered canvas with tools centered. */
public final class PremadeCategorySettingsPage extends GuiScreen {
    private static final String PERMISSION = "perplayerkit.editkitroom";
    private final GUI gui;
    private final String id;

    private PremadeCategorySettingsPage(GUI gui, Player player, String id) {
        super(gui, player, GuiLayouts.ROWS_DOCK_TOOLS, GuiFrame.Density.BORDER, PERMISSION, null);
        this.gui = gui;
        this.id = id;
    }

    public static void open(GUI gui, Player player, String categoryId) {
        if (!ActionGuards.allowed(player, PERMISSION)) return;
        if (PremadeStore.category(categoryId) == null) return;
        new PremadeCategorySettingsPage(gui, player, categoryId).show();
    }

    @Override
    protected String title() {
        PremadeStore.Category c = PremadeStore.category(id);
        return GuiMenuFactory.sectionTitle(kits(), "Category <dark_gray>\u00BB <white>" + (c == null ? "?" : c.name()));
    }

    @Override
    protected void renderContent(StaticPane content) {
        PremadeStore.Category c = PremadeStore.category(id);
        if (c == null) return;

        GuiItem rename = wrap(GuiTheme.tool(Material.NAME_TAG, "Rename", "Now: " + c.name(), "Click", "type a name"), (p, e) -> {
            SoundManager.playClick(p);
            GuiChatPrompt.ask(gui.plugin(), p, "New name for " + c.name() + ":", text -> {
                String name = PremadeStore.cleanName(text);
                if (name != null) {
                    PremadeStore.renameCategory(id, name);
                    GuiChatPrompt.say(p, GuiTheme.ok("Renamed to " + name));
                }
                open(gui, p, id);
            }, () -> open(gui, p, id));
        }, player);

        GuiItem icon = wrap(GuiTheme.tool(c.icon(), "Icon", "The tab icon", "Click with an item", "use it"), (p, e) -> {
            Material chosen = GuiTheme.cursorMaterial(e);
            if (chosen == null) {
                GuiChatPrompt.say(p, GuiTheme.warn("Pick up an item first, then click Icon."));
                return;
            }
            PremadeStore.setCategoryIcon(id, chosen);
            SoundManager.playSuccess(p);
            refresh();
        }, player);

        GuiItem delete = wrap(GuiTheme.dangerTool(Material.LAVA_BUCKET, "Delete category", "Its kits are kept", "Click", "delete"), (p, e) -> {
            SoundManager.playClick(p);
            GuiConfirm.open(p, "Delete " + c.name() + "?",
                    new String[]{"The kits inside are kept,", "they just lose this category."}, true, PERMISSION,
                    () -> {
                        PremadeStore.deleteCategory(id);
                        PremadePage.open(gui, p, null);
                    },
                    () -> open(gui, p, id));
        }, player);

        GuiLayouts.centerItems(content, List.of(rename, icon, delete), GuiLayouts.DOCK_TOOLS_ROW);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this).back((p, e) -> PremadePage.open(gui, p, id)).applyTo(chrome);
    }
}
