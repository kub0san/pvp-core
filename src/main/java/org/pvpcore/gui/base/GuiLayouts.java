package org.pvpcore.gui.base;

import org.pvpcore.features.kits.KitContents;

/**
 * Slot and row constants for the GUI. Navigation screens are
 * "top frame + content rows + dock" (see {@link GuiDock} / {@link GuiFrame.Density#BORDER});
 * editors, the kit room and the preview are fixed full-canvas layouts whose slot
 * contracts are listed here and verified by {@link GuiLayoutChecks}.
 */
public final class GuiLayouts {
    private GuiLayouts() {
    }

    public static final int COLUMNS = 9;

    /**
     * First content row on {@link GuiFrame.Density#BORDER} screens.
     * Row 0 is the top glass frame.
     */
    public static final int BORDER_CONTENT_START = 1;

    /** Absolute row for the {@code relative}-th content row on a bordered screen. */
    public static int contentRow(int relative) {
        return BORDER_CONTENT_START + relative;
    }

    // ---- Premade kits (tabs sit on the first content row) ----
    public static final int TABS_ROW = BORDER_CONTENT_START;
    public static final int PREMADE_CONTENT_ROW_ONE = BORDER_CONTENT_START + 1;

    // ---- Editors ----
    /** Total slots of a 6-row chest. */
    public static final int MENU_SIZE = 54;

    /** First slot of the footer row (row 5) of a 6-row editor. */
    public static final int FOOTER_START = 45;

    /** Kit layout content: slots 0-40 (36 main inventory plus 5 armor). */
    public static final int KIT_CONTENT_START = 0;
    public static final int KIT_CONTENT_END = KitContents.INVENTORY_SIZE; // 41, exclusive

    /** Ender chest layout content: slots 0-26, filling the compact four-row editor. */
    public static final int EC_CONTENT_START = 0;
    public static final int EC_CONTENT_END = EC_CONTENT_START + KitContents.ENDERCHEST_SIZE; // 27, exclusive

    /**
     * Kit layouts keep boots, leggings, chestplate, helmet and offhand in
     * row 4, columns 0-4. The divider therefore starts at column 5 for a kit
     * editor; it is never a full row there.
     */
    public static final int EDITOR_ARMOR_SLOT_START = 36;
    public static final int EDITOR_ARMOR_SLOT_END = 41; // exclusive
    public static final int EDITOR_DIVIDER_FIRST_COL = 5;

    /** Divider row used by editors whose row 4 is free (EC layouts). */
    public static final int EDITOR_DIVIDER_ROW = 4;
    /** Footer row shared by every editor. */
    public static final int EDITOR_FOOTER_ROW = 5;

    /** The editor footer contract, as absolute slots of row 5. */
    public static final int EDITOR_LABEL_START = 45; // armor labels, columns 0-4
    public static final int EDITOR_MODE_SLOT = 50;
    public static final int EDITOR_IMPORT_SLOT = 51;
    public static final int EDITOR_CLEAR_SLOT = 52;
    public static final int EDITOR_BACK_SLOT = 53;

    // ---- Kit room item editor: rows 0-4 are all content ----
    public static final int KIT_ROOM_CONTENT_START = 0;
    public static final int KIT_ROOM_CONTENT_END = KitContents.ROOM_SIZE; // 45, exclusive; row 5 is the footer

    /** The kit-room page list shows 18 pages, on the two content rows under the top frame. */
    public static final int KIT_ROOM_PAGES_PER_SCREEN = 18;

    // ---- Kit room editor footer ----
    public static final int FOOTER_SAVE_COL = 8;

    // ---- Total chest rows per screen type ----
    public static final int ROWS_CONFIRM = 4;
    /** Legacy six-row editor (kit room editor, customize-family screens). */
    public static final int ROWS_EDITOR = 6;
    /** Compact item editors: five rows for a kit, four for an ender chest. */
    public static final int ROWS_EDITOR_KIT = 5;
    public static final int ROWS_EDITOR_EC = 4;

    /**
     * Compact editor controls occupy the last row, immediately after the content.
     * The kit editor keeps its armor in row 4, so controls start at column 5;
     * the ender chest editor's whole last row is free.
     */
    public static final int EDITOR_KIT_CONTROL_START = KIT_CONTENT_END; // 41
    public static final int EDITOR_EC_CONTROL_START = EC_CONTENT_END; // 27
    public static final int EDITOR_KIT_BACK_SLOT = ROWS_EDITOR_KIT * COLUMNS - 1; // 44
    public static final int EDITOR_EC_BACK_SLOT = EDITOR_EC_CONTROL_START + 3; // 30

    // ---- Kit cosmetics customize screen ----
    public static final int EDITOR_CUSTOMIZE_SLOT = 41;
    public static final int CUSTOMIZE_TARGET_FIRST = 0;
    public static final int CUSTOMIZE_PREVIEW_SLOT = 50;
    public static final int CUSTOMIZE_BACK_SLOT = 45;
    public static final int CUSTOMIZE_RESET_SLOT = 46;
    public static final int CUSTOMIZE_REMOVE_SLOT = 47;
    public static final int CUSTOMIZE_APPLY_SLOT = 53;
    public static final int CUSTOMIZE_PICKER_FIRST = 9;
    public static final int CUSTOMIZE_PICKER_LAST = 44;
    public static final int RENAME_TARGET_FIRST = 0;
    public static final int RENAME_TARGET_LAST = 40;
    public static final int RENAME_BACK_SLOT = 53;

    // ---- Kit cosmetics: three-row customise hub and browser screens ----
    // These are separate from the legacy six-row customize constants above. The
    // legacy columns stay untouched for old screens; only the new screens below
    // consume this contract. GuiLayoutChecks.checkCosmeticLayouts() pins it.

    /** Customise hub: info / showcase / staged state on row 0, actions on row 1, hint on row 2. */
    public static final int COSMETIC_HUB_ROWS = 3;
    public static final int COSMETIC_HUB_SIZE = 27;
    public static final int COSMETIC_HUB_INFO_SLOT = 0;
    public static final int COSMETIC_HUB_PREVIEW_SLOT = 4;
    public static final int COSMETIC_HUB_STATE_SLOT = 8;
    public static final int COSMETIC_HUB_RENAME_SLOT = 9;
    public static final int COSMETIC_HUB_ENCHANT_SLOT = 11;
    public static final int COSMETIC_HUB_TRIM_SLOT = 13;
    public static final int COSMETIC_HUB_APPLY_SLOT = 15;
    public static final int COSMETIC_HUB_CANCEL_SLOT = 17;
    public static final int COSMETIC_HUB_HINT_SLOT = 21;

    /** Shared browser canvas: 18 entries, then preview / state / hint, then six nav controls. */
    public static final int COSMETIC_BROWSER_ROWS = 3;
    public static final int COSMETIC_BROWSER_SIZE = 27;
    public static final int COSMETIC_BROWSER_ENTRIES_PER_PAGE = 18;
    public static final int COSMETIC_BROWSER_ENTRY_FIRST = 0;
    public static final int COSMETIC_BROWSER_ENTRY_LAST = 17;
    public static final int COSMETIC_BROWSER_PREVIEW_SLOT = 18;
    public static final int COSMETIC_BROWSER_STATE_SLOT = 19;
    public static final int COSMETIC_BROWSER_HINT_SLOT = 20;

    /** Enchant browser nav: Back / Prev / Next / Apply / Cancel / Page. */
    public static final int COSMETIC_ENCHANT_BACK_SLOT = 21;
    public static final int COSMETIC_ENCHANT_PREV_SLOT = 22;
    public static final int COSMETIC_ENCHANT_NEXT_SLOT = 23;
    public static final int COSMETIC_ENCHANT_APPLY_SLOT = 24;
    public static final int COSMETIC_ENCHANT_CANCEL_SLOT = 25;
    public static final int COSMETIC_ENCHANT_PAGE_SLOT = 26;

    /** Trim browser nav: Mode / Prev / Next / Page / Remove / Back. */
    public static final int COSMETIC_TRIM_MODE_SLOT = 21;
    public static final int COSMETIC_TRIM_PREV_SLOT = 22;
    public static final int COSMETIC_TRIM_NEXT_SLOT = 23;
    public static final int COSMETIC_TRIM_PAGE_SLOT = 24;
    public static final int COSMETIC_TRIM_REMOVE_SLOT = 25;
    public static final int COSMETIC_TRIM_BACK_SLOT = 26;

    // ---- Public kit preview (5 rows, full canvas) ----
    public static final int ROWS_PREVIEW = 5;

    /**
     * Row 4 of the preview: cols 0-4 hold the kit's armor (written straight
     * into the inventory), cols 5-6 are the only free cells and hold the
     * filler, cols 7-8 hold Back and Load. Nothing else may be painted in
     * that row, or a redraw would wipe the armor.
     */
    public static final int PREVIEW_FILLER_FIRST_COL = 5;
    public static final int PREVIEW_FILLER_LAST_COL = 6;
    /** Back and Load sit in the last two slots of the last row. */
    public static final int PREVIEW_BACK_SLOT = (ROWS_PREVIEW - 1) * COLUMNS + COLUMNS - 2; // 43
    public static final int PREVIEW_LOAD_SLOT = (ROWS_PREVIEW - 1) * COLUMNS + COLUMNS - 1; // 44

    // ---- Bordered dock layouts: top frame + content + dock ----
    /** Top frame, kits, ender chests, quick actions, dock. */
    public static final int ROWS_DASHBOARD = 5;
    public static final int DASH_KITS_ROW = BORDER_CONTENT_START;
    public static final int DASH_ENDERCHESTS_ROW = BORDER_CONTENT_START + 1;
    public static final int DASH_ACTIONS_ROW = BORDER_CONTENT_START + 2;
    public static final int DASH_DOCK_ROW = ROWS_DASHBOARD - 1;
    /** Slots shown per page on the dashboard (one row). */
    public static final int DASH_SLOTS_PER_PAGE = COLUMNS;

    /**
     * Framed tool strip: top frame, breathing room, tools, dock.
     * Tools are centered on {@link #contentRow}(1).
     */
    public static final int ROWS_DOCK_TOOLS = 5;
    public static final int DOCK_TOOLS_ROW = BORDER_CONTENT_START + 1;

    /** Top frame, two content rows, dock (page list, settings hub). */
    public static final int ROWS_DOCK_LIST18 = rowsFor(2);
    /** Top frame, three content rows, dock (settings group / list, RTP management). */
    public static final int ROWS_DOCK_LIST27 = rowsFor(3);
    /** Top frame, tabs + two kit rows, dock. */
    public static final int ROWS_PREMADE_DOCK = rowsFor(3);
    public static final int PREMADE_KITS_PER_PAGE_DOCK = 2 * COLUMNS;
    public static final int DOCK_ACTION_COL_A = 7;
    public static final int DOCK_ACTION_COL_B = 8;

    /** Content rows plus top frame plus dock, clamped to a valid chest size. */
    public static int rowsFor(int contentRows) {
        return Math.max(3, Math.min(6, contentRows + 2));
    }

    /**
     * Centres a list of items in a row, perfectly.
     * Uses a step of 2 (gaps between items) if they fit, otherwise squeezes them.
     */
    public static void centerItems(com.github.stefvanschie.inventoryframework.pane.StaticPane pane,
                                   java.util.List<com.github.stefvanschie.inventoryframework.gui.GuiItem> items,
                                   int row) {
        int count = items.size();
        if (count == 0) return;

        // If we can fit with a 1-slot gap between each item (e.g. 4 items = 7 width), do it.
        int step = (count * 2 - 1) <= COLUMNS ? 2 : 1;
        int width = (count - 1) * step + 1;
        int startCol = (COLUMNS - width) / 2;

        for (int i = 0; i < count; i++) {
            pane.addItem(items.get(i), startCol + (i * step), row);
        }
    }
}
