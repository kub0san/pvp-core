package org.pvpcore.gui.base;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.util.kits.SoundManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * The dock: the bottom row of a screen. On bordered menus it sits on the bottom
 * glass frame; chrome paints over the glass.
 *
 * <pre>
 *  0      1  2   3     4      5     6  7  8
 * [Back] [ ] [ ] [◀]  [Page]  [▶]  [ ] [ ] [action]
 * </pre>
 *
 * Empty dock cells are glass (painted by {@link GuiFrame.Density#BORDER}).
 * The page item is one control: click = next, right-click = previous
 * (both wrap around), shift-click = last / first.
 *
 * <pre>{@code
 * GuiDock.of(this)
 *     .back((p, e) -> gui.openMain(p))
 *     .pager(pager, target -> { page = target; refresh(); })
 *     .action(8, saveItem, saveClick)
 *     .applyTo(chrome);
 * }</pre>
 */
public final class GuiDock {
    public static final int COL_BACK = 0;
    public static final int COL_PREV = 3;
    public static final int COL_PAGE = 4;
    public static final int COL_NEXT = 5;

    private final GuiScreen screen;
    private final int row;
    private GuiClick backClick;
    private GuiPager pager;
    private IntConsumer goTo;
    private final Map<Integer, GuiItem> actions = new HashMap<>();

    private GuiDock(GuiScreen screen) {
        this.screen = screen;
        this.row = screen.dockRow();
    }

    public static GuiDock of(GuiScreen screen) {
        return new GuiDock(screen);
    }

    public GuiDock back(GuiClick click) {
        this.backClick = click;
        return this;
    }

    public GuiDock pager(GuiPager pager, IntConsumer goToPage) {
        this.pager = pager;
        this.goTo = goToPage;
        return this;
    }

    /** A custom button in dock column {@code col} (0-8). Avoid 0 and 3-5 when back/pager are used. */
    public GuiDock action(int col, ItemStack item, GuiClick click) {
        actions.put(col, GuiScreen.wrap(item, click, screen.player()));
        return this;
    }

    public void applyTo(StaticPane chrome) {
        if (backClick != null) {
            chrome.addItem(GuiScreen.wrap(GuiTheme.back(), (p, e) -> {
                SoundManager.playClick(p);
                backClick.on(p, e);
            }, screen.player()), COL_BACK, row);
        }

        if (pager != null && goTo != null && pager.maxPages() > 1) {
            final int page = pager.currentPage();
            final int max = pager.maxPages();

            if (page > 0) {
                chrome.addItem(GuiScreen.wrap(GuiTheme.arrow(false, page - 1, max),
                        (p, e) -> go(p, page - 1), screen.player()), COL_PREV, row);
            }
            chrome.addItem(GuiScreen.wrap(GuiTheme.pageItem(page, max), (p, e) -> {
                int target = switch (e.getClick()) {
                    case SHIFT_LEFT -> max - 1;
                    case SHIFT_RIGHT -> 0;
                    case RIGHT -> (page - 1 + max) % max;
                    default -> (page + 1) % max;
                };
                go(p, target);
            }, screen.player()), COL_PAGE, row);
            if (page < max - 1) {
                chrome.addItem(GuiScreen.wrap(GuiTheme.arrow(true, page + 1, max),
                        (p, e) -> go(p, page + 1), screen.player()), COL_NEXT, row);
            }
        }

        actions.forEach((col, item) -> chrome.addItem(item, col, row));
    }

    private void go(org.bukkit.entity.Player p, int target) {
        SoundManager.playClick(p);
        goTo.accept(target);
    }
}
