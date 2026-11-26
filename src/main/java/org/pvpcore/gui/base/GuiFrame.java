package org.pvpcore.gui.base;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.util.kits.StyleManager;

/**
 * Paints the decorative frame. The frame is one of the three layers of every
 * framed screen: it sits at the bottom of the pane stack, content and chrome are
 * painted over it. GuiFrame slots are never interactive -- GuiGuard on the
 * chest cancels every click, and no click handler is attached to frame items.
 */
public final class GuiFrame {
    public enum Density {
        /**
         * Top and bottom rows of glass. Navigation / list screens use this so
         * menus feel framed and large; content starts on row 1 and the dock
         * overlays the bottom row (see {@link GuiDock}).
         */
        BORDER,
        /**
         * Bottom row only. Prefer {@link #BORDER} for new screens; kept for
         * any special-case compact surfaces.
         */
        DOCK,
        /**
         * Divider row 4, columns 5-8 on the legacy six-row kit editor. The compact
         * five-row editor paints nothing: its last row is armor plus controls.
         */
        EDITOR_ARMOR,
        /** Top frame plus divider on the legacy EC editor; nothing on the compact four-row one. */
        EDITOR_EC,
        /** No frame. Full-canvas screens that paint their own decoration. */
        NONE
    }

    private GuiFrame() {
    }

    public static void paint(StaticPane frame, int rows, Density density, StyleManager style) {
        ItemStack glass = GuiTheme.filler(style);
        switch (density) {
            case BORDER -> {
                row(frame, 0, glass);
                if (rows > 1) {
                    row(frame, rows - 1, glass);
                }
            }
            case DOCK -> row(frame, rows - 1, glass);
            case EDITOR_ARMOR -> {
                // Compact kit editor: row 4 holds armor and controls, so no glass there.
                if (rows != GuiLayouts.ROWS_EDITOR_KIT) {
                    row(frame, GuiLayouts.EDITOR_DIVIDER_ROW,
                            GuiLayouts.EDITOR_DIVIDER_FIRST_COL, GuiLayouts.COLUMNS, glass);
                }
            }
            case EDITOR_EC -> {
                // Compact EC editor: rows 0-2 are content and row 3 is controls.
                if (rows != GuiLayouts.ROWS_EDITOR_EC) {
                    row(frame, 0, glass);
                    row(frame, GuiLayouts.EDITOR_DIVIDER_ROW, glass);
                }
            }
            case NONE -> { /* nothing */ }
        }
    }

    public static void paint(StaticPane frame, int rows, Density density, KitsContext kits) {
        paint(frame, rows, density, kits == null ? null : kits.styleManager());
    }

    /** Paints a full row. Public so a screen can compose a custom density. */
    public static void row(StaticPane frame, int row, StyleManager style) {
        row(frame, row, GuiTheme.filler(style));
    }

    /** Paints a full row with an explicit glass stack. */
    public static void row(StaticPane frame, int row, ItemStack glass) {
        row(frame, row, 0, GuiLayouts.COLUMNS, glass);
    }

    /** Paints one row between two columns. Public for the editor armor exception. */
    public static void row(StaticPane frame, int row, int fromCol, int toColExclusive, StyleManager style) {
        row(frame, row, fromCol, toColExclusive, GuiTheme.filler(style));
    }

    public static void row(StaticPane frame, int row, int fromCol, int toColExclusive, ItemStack glass) {
        if (row < 0 || glass == null) return;
        for (int col = Math.max(0, fromCol); col < toColExclusive && col < GuiLayouts.COLUMNS; col++) {
            frame.addItem(new GuiItem(glass.clone()), col, row);
        }
    }
}
