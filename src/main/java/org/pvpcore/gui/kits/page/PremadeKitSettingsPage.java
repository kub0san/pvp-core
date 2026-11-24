package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.features.kits.PublicKit;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.kits.data.PremadeStore;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;

/** Staff: settings for one premade kit. Large bordered canvas with tools centered. */
public final class PremadeKitSettingsPage extends GuiScreen {
    private static final String PERMISSION = "perplayerkit.editkitroom";
    private final GUI gui;
    private final PublicKit kit;

    private PremadeKitSettingsPage(GUI gui, Player player, PublicKit kit) {
        super(gui, player, GuiLayouts.ROWS_DOCK_TOOLS, GuiFrame.Density.BORDER, PERMISSION, null);
        this.gui = gui;
        this.kit = kit;
    }

    public static void open(GUI gui, Player player, String kitId) {
        if (!ActionGuards.allowed(player, PERMISSION)) return;
        PublicKit kit = PremadeStore.kit(gui.kits(), kitId);
        if (kit == null) return;
        new PremadeKitSettingsPage(gui, player, kit).show();
    }

    @Override
    protected String title() {
        return GuiMenuFactory.sectionTitle(kits(), "Premade <dark_gray>\u00BB <white>" + kit.name);
    }

    @Override
    protected void renderContent(StaticPane content) {
        GuiItem edit = wrap(GuiTheme.tool(Material.CHEST, "Edit items", "Opens the item editor", "Click", "open"), (p, e) -> {
            SoundManager.playClick(p);
            gui.OpenPublicKitEditor(p, kit.id);
        }, player);

        GuiItem rename = wrap(GuiTheme.tool(Material.NAME_TAG, "Rename", "Now: " + kit.name, "Click", "type a name"), (p, e) -> {
            SoundManager.playClick(p);
            GuiChatPrompt.ask(gui.plugin(), p, "New name for " + kit.name + ":", text -> {
                String name = PremadeStore.cleanName(text);
                if (name != null) {
                    PremadeStore.renameKit(kits(), kit, name);
                    GuiChatPrompt.say(p, GuiTheme.ok("Renamed to " + name));
                }
                open(gui, p, kit.id);
            }, () -> open(gui, p, kit.id));
        }, player);

        GuiItem icon = wrap(GuiTheme.tool(kit.icon, "Icon", "Shown in the premade list", "Click with an item", "use it"), (p, e) -> {
            Material chosen = GuiTheme.cursorMaterial(e);
            if (chosen == null) {
                GuiChatPrompt.say(p, GuiTheme.warn("Pick up an item first, then click Icon."));
                return;
            }
            PremadeStore.setKitIcon(kits(), kit, chosen);
            SoundManager.playSuccess(p);
            refresh();
        }, player);

        PremadeStore.Category current = PremadeStore.category(PremadeStore.categoryOf(kit.id));
        GuiItem category = wrap(GuiTheme.named(Material.BOOKSHELF, GuiTheme.TEXT + "<bold>", "Category",
                GuiTheme.value("Now", current == null ? "none" : current.name()),
                GuiTheme.hint("Click / Right", "next · previous")), (p, e) -> {
            SoundManager.playClick(p);
            cycleCategory(e.getClick().isRightClick() ? -1 : 1);
            refresh();
        }, player);

        GuiItem delete = wrap(GuiTheme.dangerTool(Material.LAVA_BUCKET, "Delete kit", "Removes the kit and its items",
                "Click", "delete"), (p, e) -> {
            SoundManager.playClick(p);
            GuiConfirm.open(p, "Delete " + kit.name + "?", new String[]{"The kit and its items are removed."}, true, PERMISSION,
                    () -> {
                        PremadeStore.deleteKit(kits(), kit);
                        PremadePage.open(gui, p);
                    },
                    () -> open(gui, p, kit.id));
        }, player);

        GuiLayouts.centerItems(content, List.of(edit, rename, icon, category, delete), GuiLayouts.DOCK_TOOLS_ROW);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this).back((p, e) -> PremadePage.open(gui, p)).applyTo(chrome);
    }

    private void cycleCategory(int step) {
        List<String> choices = new ArrayList<>();
        choices.add(null);
        for (PremadeStore.Category c : PremadeStore.categories()) choices.add(c.id());
        int index = choices.indexOf(PremadeStore.categoryOf(kit.id));
        int next = (Math.max(index, 0) + step + choices.size()) % choices.size();
        PremadeStore.assign(kit.id, choices.get(next));
    }
}
