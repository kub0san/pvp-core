package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.base.GuiSettingsSchema;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A list-valued setting. Bordered: 27 entries on three content rows, dock with Back,
 * pager and "Add". Entries that are item names show their item.
 */
public final class SettingsListPage extends GuiScreen {
    private final GUI gui;
    private final GuiSettingsSchema.Def def;
    private final GuiSettingsSupport.Return back;
    private int page;
    private List<String> entries;

    public SettingsListPage(GUI gui, Player player, GuiSettingsSchema.Def def, GuiSettingsSupport.Return back) {
        super(gui, player, GuiLayouts.ROWS_DOCK_LIST27, GuiFrame.Density.BORDER, "perplayerkit.admin", null);
        this.gui = gui;
        this.def = def;
        this.back = back;
    }

    public GuiSettingsSupport.Return back() {
        return back;
    }

    public String backGroupKey() {
        return back.groupKey();
    }

    public int backPage() {
        return back.page();
    }

    private List<String> entries() {
        if (entries == null) {
            Object value = GuiSettingsSupport.displayed(kits(), player, def);
            entries = new ArrayList<>();
            if (value instanceof List<?> list) {
                for (Object entry : list) entries.add(String.valueOf(entry));
            }
        }
        return entries;
    }

    private void writeBack() {
        GuiSettingsSupport.stage(player, def, new ArrayList<>(entries()));
    }

    private GuiPager pager() {
        return GuiPager.of(page, entries().size(), GuiSettingsSchema.ENTRIES_PER_SCREEN);
    }

    @Override
    protected String title() {
        return GuiMenuFactory.sectionTitle(kits(), def.label() + " <dark_gray>\u00BB <white>" + entries().size() + " entries");
    }

    @Override
    protected void renderContent(StaticPane content) {
        GuiPager pager = pager();
        page = pager.currentPage();
        List<String> list = entries();
        int from = pager.from();
        int to = pager.to(list.size());
        for (int index = from; index < to; index++) {
            int i = index - from;
            String entry = list.get(index);
            Material matched = Material.matchMaterial(entry);
            Material icon = matched != null && matched.isItem() && !matched.isAir() ? matched : Material.PAPER;
            content.addItem(wrap(GuiTheme.named(icon, GuiTheme.TEXT, entry,
                            GuiTheme.dim("Entry " + (index + 1)), GuiTheme.hint("Click", "remove")),
                    (p, e) -> {
                        SoundManager.playClick(p);
                        list.remove(entry);
                        writeBack();
                        refresh();
                    }, player), i % GuiLayouts.COLUMNS, GuiLayouts.contentRow(i / GuiLayouts.COLUMNS));
        }
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this)
                .back((p, e) -> GuiSettingsSupport.reopen(back, p))
                .pager(pager(), target -> {
                    page = target;
                    refresh();
                })
                .action(GuiLayouts.DOCK_ACTION_COL_B, GuiTheme.named(Material.WRITABLE_BOOK, GuiTheme.GREEN + "<bold>",
                        "+ Add", GuiTheme.dim("Type in chat"), GuiTheme.hint("Click", "add")), (p, e) -> {
                    SoundManager.playClick(p);
                    askAdd();
                })
                .applyTo(chrome);
    }

    private void askAdd() {
        GuiChatPrompt.ask(gui.plugin(), player, "Add to " + def.label() + ":", text -> {
            String entry = text.trim();
            String problem = def.check() == null ? null : def.check().problem(entry);
            if (problem != null) {
                GuiChatPrompt.say(player, GuiTheme.fail(problem));
            } else if (entries().contains(entry)) {
                GuiChatPrompt.say(player, GuiTheme.warn(entry + " is already in the list."));
            } else {
                entries().add(entry);
                writeBack();
                SoundManager.playSuccess(player);
            }
            new SettingsListPage(gui, player, def, back).show();
        }, () -> new SettingsListPage(gui, player, def, back).show());
    }
}
