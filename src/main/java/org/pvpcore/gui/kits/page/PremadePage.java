package org.pvpcore.gui.kits.page;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.features.kits.PublicKit;
import org.pvpcore.gui.base.*;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.kits.data.PremadeStore;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Premade kits. 5 bordered rows:
 *
 * <pre>
 *  row 0     top glass frame
 *  row 1     category tabs (All + one per category; arrows scroll when there are many)
 *  rows 2-3  18 kit cards per page
 *  row 4     dock: Back, pager, and for staff "New category" / "New kit"
 * </pre>
 */
public final class PremadePage extends GuiScreen {
    private static final String PERMISSION = "perplayerkit.publickit";
    private static final String STAFF = "perplayerkit.editkitroom";
    private static final int TABS_PER_PAGE = 7;

    /** The category each player last looked at, so Back returns to it. "" means All. */
    private static final Map<UUID, String> LAST = new ConcurrentHashMap<>();

    private final GUI gui;
    private final boolean staff;
    private String category; // null = all kits
    private int page;
    private int tabPage;

    private PremadePage(GUI gui, Player player, String category) {
        super(gui, player, GuiLayouts.ROWS_PREMADE_DOCK, GuiFrame.Density.BORDER, PERMISSION, LocationFeature.PUBLIC_KITS);
        this.gui = gui;
        this.staff = player.hasPermission(STAFF);
        this.category = category;
    }

    public static void open(GUI gui, Player player) {
        String last = LAST.getOrDefault(player.getUniqueId(), "");
        open(gui, player, last.isEmpty() ? null : last);
    }

    public static void open(GUI gui, Player player, String category) {
        if (!ActionGuards.allowed(player, PERMISSION)) return;
        String valid = PremadeStore.category(category) == null ? null : category;
        LAST.put(player.getUniqueId(), valid == null ? "" : valid);
        new PremadePage(gui, player, valid).show();
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }

    private List<PublicKit> kitsIn(String categoryId) {
        List<PublicKit> out = new ArrayList<>();
        for (PublicKit kit : PremadeStore.kits(kits())) {
            if (categoryId != null && !categoryId.equals(PremadeStore.categoryOf(kit.id))) continue;
            if (!staff && !kits().kitManager().hasPublicKit(kit.id)) continue;
            out.add(kit);
        }
        return out;
    }

    private GuiPager pager() {
        return GuiPager.of(page, kitsIn(category).size(), GuiLayouts.PREMADE_KITS_PER_PAGE_DOCK);
    }

    private int tabPages() {
        return Math.max(1, (PremadeStore.categories().size() + 1 + TABS_PER_PAGE - 1) / TABS_PER_PAGE);
    }

    @Override
    protected String title() {
        PremadeStore.Category c = PremadeStore.category(category);
        return GuiMenuFactory.sectionTitle(kits(), "Premade Kits" + (c == null ? "" : " <dark_gray>\u00BB <white>" + c.name()));
    }

    // ------------------------------------------------------------------ content

    private record Tab(String id, Material icon, String name) {
    }

    private List<Tab> tabs() {
        List<Tab> out = new ArrayList<>();
        out.add(new Tab(null, Material.BOOKSHELF, "All"));
        for (PremadeStore.Category c : PremadeStore.categories()) {
            out.add(new Tab(c.id(), c.icon(), c.name()));
        }
        return out;
    }

    @Override
    protected void renderContent(StaticPane content) {
        tabPage = Math.max(0, Math.min(tabPage, tabPages() - 1));
        renderTabs(content);

        List<PublicKit> kits = kitsIn(category);
        GuiPager pager = pager();
        page = pager.currentPage();
        List<PublicKit> visible = pager.slice(kits);
        for (int i = 0; i < visible.size(); i++) {
            PublicKit kit = visible.get(i);
            content.addItem(wrap(kitItem(kit), (p, e) -> kitClicked(p, e.getClick().isRightClick(),
                            e.getClick().isShiftClick(), kit), player),
                    i % GuiLayouts.COLUMNS, GuiLayouts.PREMADE_CONTENT_ROW_ONE + i / GuiLayouts.COLUMNS);
        }
    }

    private void renderTabs(StaticPane content) {
        List<Tab> all = tabs();
        for (int i = 0; i < TABS_PER_PAGE; i++) {
            int index = tabPage * TABS_PER_PAGE + i;
            if (index >= all.size()) break;
            Tab tab = all.get(index);
            boolean selected = java.util.Objects.equals(category, tab.id());
            ItemStack item = GuiCard.categoryTab(tab.icon(), tab.name(), selected, kitsIn(tab.id()).size(),
                    staff && tab.id() != null);
            content.addItem(wrap(item, (p, e) -> tabClicked(p, e.getClick().isRightClick(), tab), player),
                    1 + i, GuiLayouts.TABS_ROW);
        }
        int tabPages = tabPages();
        if (tabPage > 0) {
            content.addItem(wrap(GuiTheme.arrow(false, tabPage - 1, tabPages), (p, e) -> moveTabs(p, tabPage - 1), player),
                    0, GuiLayouts.TABS_ROW);
        }
        if (tabPage < tabPages - 1) {
            content.addItem(wrap(GuiTheme.arrow(true, tabPage + 1, tabPages), (p, e) -> moveTabs(p, tabPage + 1), player),
                    GuiLayouts.COLUMNS - 1, GuiLayouts.TABS_ROW);
        }
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock dock = GuiDock.of(this)
                .back((p, e) -> gui.OpenMainMenu(p, GUI.lastMainMenuPageOf(p)))
                .pager(pager(), target -> {
                    page = target;
                    refresh();
                });
        if (staff) {
            dock.action(GuiLayouts.DOCK_ACTION_COL_A, GuiCard.newCategoryTab(), (p, e) -> newCategory(p));
            dock.action(GuiLayouts.DOCK_ACTION_COL_B, GuiCard.newKitCard(), (p, e) -> newKit(p));
        }
        dock.applyTo(chrome);
    }

    // ------------------------------------------------------------------ cards + clicks

    private ItemStack kitItem(PublicKit kit) {
        boolean ready = kits().kitManager().hasPublicKit(kit.id);
        if (!ready) {
            return GuiCard.kitCard(kit.icon, kit.name, GuiCard.CardState.EMPTY);
        }
        ItemStack[] contents = kits().kitManager().peekPublicKit(kit.id);
        return GuiCard.kitCard(kit.icon, kit.name, contents,
                staff ? GuiCard.CardState.STAFF_EDIT : GuiCard.CardState.PREVIEW);
    }

    private void kitClicked(Player p, boolean right, boolean shift, PublicKit kit) {
        boolean ready = kits().kitManager().hasPublicKit(kit.id);
        if (staff && right) {
            SoundManager.playClick(p);
            PremadeKitSettingsPage.open(gui, p, kit.id);
            return;
        }
        if (!ready) {
            if (staff) {
                SoundManager.playClick(p);
                gui.OpenPublicKitEditor(p, kit.id);
            }
            return;
        }
        if (!ActionGuards.allowed(p, PERMISSION) || !ActionGuards.dataReady(p)) return;
        SoundManager.playClick(p);
        if (shift) {
            kits().kitManager().loadPublicKit(p, kit.id);
            p.closeInventory();
        } else {
            gui.ViewPublicKitMenu(p, kit.id);
        }
    }

    private void tabClicked(Player p, boolean right, Tab tab) {
        SoundManager.playClick(p);
        if (staff && right && tab.id() != null) {
            PremadeCategorySettingsPage.open(gui, p, tab.id());
            return;
        }
        category = tab.id();
        page = 0;
        LAST.put(p.getUniqueId(), category == null ? "" : category);
        refresh();
    }

    private void moveTabs(Player p, int target) {
        SoundManager.playClick(p);
        tabPage = target;
        refresh();
    }

    /** Staff: ask for a name, create the kit, and open the editor to fill it. */
    private void newKit(Player p) {
        SoundManager.playClick(p);
        String inCategory = category;
        GuiChatPrompt.ask(gui.plugin(), p, "Name of the new kit:", text -> {
            String name = PremadeStore.cleanName(text);
            if (name == null) {
                PremadePage.open(gui, p, inCategory);
                return;
            }
            PublicKit kit = PremadeStore.createKit(gui.kits(), name, Material.CHEST);
            if (kit == null) {
                GuiChatPrompt.say(p, GuiTheme.warn("Saved, but it only shows up after /pvp-core reload."));
                PremadePage.open(gui, p, inCategory);
                return;
            }
            if (inCategory != null) {
                PremadeStore.assign(kit.id, inCategory);
            }
            gui.OpenPublicKitEditor(p, kit.id);
        }, () -> PremadePage.open(gui, p, inCategory));
    }

    private void newCategory(Player p) {
        SoundManager.playClick(p);
        GuiChatPrompt.ask(gui.plugin(), p, "Name of the new category:", text -> {
            String name = PremadeStore.cleanName(text);
            if (name == null) {
                PremadePage.open(gui, p, category);
                return;
            }
            PremadePage.open(gui, p, PremadeStore.createCategory(name).id());
        }, () -> PremadePage.open(gui, p, category));
    }
}
