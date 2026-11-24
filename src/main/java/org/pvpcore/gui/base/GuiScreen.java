package org.pvpcore.gui.base;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.Pane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.util.kits.LocationFeature;

/**
 * Base class for every screen in the GUI system.
 *
 * <p>Three layers, painted in this order (later panes render over earlier ones):</p>
 * <ol>
 *   <li>{@code framePane}   -- decorative stained-glass frame (see {@link GuiFrame})</li>
 *   <li>{@code contentPane} -- cards, settings, editor slots</li>
 *   <li>{@code chromePane}  -- footer buttons and pager</li>
 * </ol>
 *
 * <p>The layers carry explicit {@link Pane.Priority} values. The
 * inventory-framework sorts panes by priority and paints the last one on top,
 * but it inserts panes of <em>equal</em> priority in reverse order. Without
 * explicit priorities the frame would therefore be painted last, and its glass
 * would cover the dock buttons that share the dock row.</p>
 *
 * <p>Subclasses implement {@link #title()}, {@link #renderContent(StaticPane)} and
 * {@link #renderChrome(StaticPane)}. The base class handles the frame, click
 * protection, the one-tick redraw, and the ordering of the panes. Nothing in
 * the base class knows about specific screens.</p>
 *
 * <p>The owner of the plugin is abstracted as a {@link GuiSession}: the
 * foundation sees a scheduler and one editor-flush hook, never the kits GUI
 * facade.</p>
 *
 * <p>Editors override {@link #protect(ChestGui)} because their content slots are
 * editable. Navigation screens keep the default {@link GuiGuard#readOnly}.</p>
 *
 * <p><strong>Direct inventory painting.</strong> Screens that write items straight
 * into the inventory after the frame/content/chrome paint must override
 * {@link #afterInventory(Inventory, boolean)}. It runs after the initial show and
 * again after every refresh, so directly written items survive refreshes.</p>
 */
public abstract class GuiScreen {
    protected final GuiSession session;
    protected final Player player;
    protected final int rows;
    private final GuiFrame.Density density;
    private final String permission;
    private final LocationFeature feature;

    protected final StaticPane contentPane;
    protected final StaticPane chromePane;
    protected final StaticPane framePane;

    private ChestGui chest;

    private final boolean paintsInventoryDirectly = declares(getClass(), "afterInventory");

    protected GuiScreen(GuiSession session, Player player, int rows,
                           GuiFrame.Density density, String permission, LocationFeature feature) {
        this.session = session;
        this.player = player;
        this.rows = rows;
        this.density = density;
        this.permission = permission;
        this.feature = feature;
        this.contentPane = new StaticPane(GuiLayouts.COLUMNS, rows, Pane.Priority.NORMAL);
        this.chromePane = new StaticPane(GuiLayouts.COLUMNS, rows, Pane.Priority.HIGH);
        this.framePane = new StaticPane(GuiLayouts.COLUMNS, rows, Pane.Priority.LOW);
    }

    // ---- accessors for subclasses and GuiDock ----

    public final GuiSession session() {
        return session;
    }

    public final Player player() {
        return player;
    }

    public final int rows() {
        return rows;
    }

    public final GuiFrame.Density density() {
        return density;
    }

    protected final ChestGui chest() {
        return chest;
    }

    protected final org.pvpcore.features.kits.KitsContext kits() {
        return session.kits();
    }


    /**
     * The footer row of an editor: always row 5, never {@code rows - 2}.
     * Editors have no bottom frame, so their footer is the last row.
     */
    public final int editorFooterRow() {
        return GuiLayouts.EDITOR_FOOTER_ROW;
    }

    /**
     * The dock row: always the last row. Screens using {@link GuiFrame.Density#DOCK}
     * put back / pager / actions there through {@link GuiDock}.
     */
    public final int dockRow() {
        return rows - 1;
    }

    // ---- contracts for subclasses ----

    protected abstract String title();

    protected abstract void renderContent(StaticPane content);

    protected abstract void renderChrome(StaticPane chrome);

    /** Override to customise the frame (e.g. accent slots). Default paints density. */
    protected void renderFrame(StaticPane frame) {
        GuiFrame.paint(frame, rows, density, kits() == null ? null : kits().styleManager());
    }

    /** Called before chest.show(player). Override to add setOnClose etc. */
    protected void configure(ChestGui chest) {
    }

    /**
     * Explicit hook for screens that paint items directly into the inventory.
     * Called after the initial show ({@code refresh == false}) and after each
     * refresh repaint ({@code refresh == true}).
     */
    protected void afterInventory(Inventory inventory, boolean refresh) {
    }

    /**
     * Applies click protection. The default guards a button-only screen. Editors
     * override this with an editable-slot guard (or an equivalent).
     */
    protected void protect(ChestGui chest) {
        GuiGuard.readOnly(chest, permission, feature, rows);
    }

    // ---- lifecycle ----

    public final void show() {
        session.flushOpenEditor(player);
        chest = new ChestGui(rows, ComponentHolder.of(titleComponent()));
        protect(chest);

        // Order matters: frame bottom, content middle, chrome top.
        chest.addPane(Slot.fromXY(0, 0), framePane);
        chest.addPane(Slot.fromXY(0, 0), contentPane);
        chest.addPane(Slot.fromXY(0, 0), chromePane);

        redraw();
        configure(chest);
        chest.show(player);
        afterInventory(chest.getInventory(), false);
    }

    public final void refresh() {
        Bukkit.getScheduler().runTask(session.plugin(), () -> {
            if (chest == null) return;
            if (player.getOpenInventory().getTopInventory() != chest.getInventory()) return;
            redraw();
            chest.update();
            if (paintsInventoryDirectly) {
                afterInventory(chest.getInventory(), true);
            }
            updateTitle(player, titleComponent());
        });
    }

    @SuppressWarnings("deprecation")
    private static void updateTitle(Player player, Component title) {
        if (title == null) return;
        player.getOpenInventory().setTitle(
                LegacyComponentSerializer.legacySection().serialize(title));
    }

    private void redraw() {
        framePane.clear();
        contentPane.clear();
        chromePane.clear();
        renderFrame(framePane);
        renderContent(contentPane);
        renderChrome(chromePane);
    }

    /** The legacy title, deserialized the same way for the chest and for refresh(). */
    private Component titleComponent() {
        return LegacyComponentSerializer.legacySection().deserialize(title());
    }

    /**
     * Whether any class in this instance's hierarchy (except GuiScreen itself)
     * declares the direct-inventory paint hook.
     */
    private static boolean declares(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c == GuiScreen.class) {
                return false;
            }
            try {
                if ("afterInventory".equals(name)) {
                    c.getDeclaredMethod(name, Inventory.class, boolean.class);
                } else {
                    c.getDeclaredMethod(name, Inventory.class);
                }
                return true;
            } catch (NoSuchMethodException ignored) {
                // Keep walking up the hierarchy.
            }
        }
        return false;
    }

    /** Wraps an item + click into a GuiItem, resolving the clicking player. */
    public static GuiItem wrap(ItemStack item, GuiClick click, Player fallback) {
        return new GuiItem(item, event -> {
            Player clicker = event.getWhoClicked() instanceof Player p ? p : fallback;
            if (clicker != null) {
                click.on(clicker, event);
            }
        });
    }
}
