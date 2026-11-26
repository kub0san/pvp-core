package org.pvpcore.gui.base;

/**
 * Sanity checks on {@link GuiLayouts}. Runs once at plugin load. If any check
 * fails the plugin refuses to start with a message naming the bad constant,
 * so a layout edit that breaks a screen fails loudly instead of producing a
 * silently wrong GUI at runtime.
 *
 * <p>These are cheap (they read constants) and catch exactly the mistakes
 * that are easiest to make in {@code GuiLayouts}: two screen types claiming the
 * same row, a footer placed on the divider row, editor math that stops
 * lining up after a change.</p>
 *
 * <p>The checks assert <em>invariants</em>, not magic numbers. "The editor
 * footer row is {@code rows - 2}" is false for editors, so it is not checked
 * that way; instead the editor footer is pinned to its own row and checked
 * against the slots that are derived from it.</p>
 *
 * <p>One invariant applies to every full-canvas screen, not just the preview:
 * <strong>items written straight into the inventory and items painted in a
 * pane must never share a slot.</strong> A full-canvas screen writes its real
 * items after the screen opens, so any pane item in the same slot is only
 * "winning" by accident -- the first redraw would paint over it. The preview
 * check below exists because that bug happened once already; the rule applies
 * to any screen of that family, including ones that do not exist yet.</p>
 */
public final class GuiLayoutChecks {

    private GuiLayoutChecks() {
    }

    private static void checkCustomizeRows() {
        if (GuiLayouts.CUSTOMIZE_PICKER_LAST >= GuiLayouts.FOOTER_START) {
            throw new IllegalStateException("CUSTOMIZE_PICKER_LAST out of range");
        }
    }

    /** Called once from {@code PvPCorePlugin#onEnable}. */
    public static void verify() {
        checkEditorRows();
        checkCompactEditorRows();
        checkKitRoomRows();
        checkPreviewRows();
        checkRowCounts();
        checkSettingsCapacity();
        checkDockRows();
        checkCustomizeRows();
    }

    // ------------------------------------------------------------------ screens

    // ------------------------------------------------------------------ editors

    private static void checkEditorRows() {
        // The editor is a 6-row chest; every absolute slot below derives from it.
        assertEqual("MENU_SIZE", GuiLayouts.MENU_SIZE, GuiLayouts.ROWS_EDITOR * GuiLayouts.COLUMNS);
        assertEqual("FOOTER_START", GuiLayouts.FOOTER_START, GuiLayouts.EDITOR_FOOTER_ROW * GuiLayouts.COLUMNS);

        // The footer must not share a row with the divider.
        if (GuiLayouts.EDITOR_FOOTER_ROW == GuiLayouts.EDITOR_DIVIDER_ROW) {
            throw new IllegalStateException(
                    "EDITOR_FOOTER_ROW (" + GuiLayouts.EDITOR_FOOTER_ROW
                            + ") must differ from EDITOR_DIVIDER_ROW");
        }
        if (GuiLayouts.EDITOR_FOOTER_ROW >= GuiLayouts.ROWS_EDITOR) {
            throw new IllegalStateException(
                    "EDITOR_FOOTER_ROW (" + GuiLayouts.EDITOR_FOOTER_ROW
                            + ") is outside a " + GuiLayouts.ROWS_EDITOR + "-row editor");
        }

        // Kit layouts keep armor in row 4, cols 0-4; the divider starts at
        // col 5 of the same row. Both must line up exactly.
        assertEqual("EDITOR_ARMOR_SLOT_START", GuiLayouts.EDITOR_ARMOR_SLOT_START,
                GuiLayouts.EDITOR_DIVIDER_ROW * GuiLayouts.COLUMNS);
        assertEqual("EDITOR_ARMOR_SLOT_END", GuiLayouts.EDITOR_ARMOR_SLOT_END,
                GuiLayouts.EDITOR_DIVIDER_ROW * GuiLayouts.COLUMNS + GuiLayouts.EDITOR_DIVIDER_FIRST_COL);

        // The kit content is rows 0-3 plus the armor in row 4, so it ends
        // exactly where the armor ends.
        assertEqual("KIT_CONTENT_START", GuiLayouts.KIT_CONTENT_START, 0);
        assertEqual("KIT_CONTENT_END", GuiLayouts.KIT_CONTENT_END, GuiLayouts.EDITOR_ARMOR_SLOT_END);

        // The ender-chest content and controls belong to the compact editor;
        // their full contract is checked by checkCompactEditorRows().

        // The editor footer contract must live inside the footer row.
        int[] contract = {
                GuiLayouts.EDITOR_LABEL_START,
                GuiLayouts.EDITOR_MODE_SLOT,
                GuiLayouts.EDITOR_IMPORT_SLOT,
                GuiLayouts.EDITOR_CLEAR_SLOT,
                GuiLayouts.EDITOR_BACK_SLOT
        };
        for (int slot : contract) {
            if (slot < GuiLayouts.FOOTER_START || slot >= GuiLayouts.MENU_SIZE) {
                throw new IllegalStateException(
                        "Editor footer slot " + slot + " is outside the footer row ("
                                + GuiLayouts.FOOTER_START + ".." + (GuiLayouts.MENU_SIZE - 1) + ")");
            }
        }
        assertDistinctAscending("editor footer contract", contract);
        assertEqual("EDITOR_LABEL_START", GuiLayouts.EDITOR_LABEL_START, GuiLayouts.FOOTER_START);
    }

    /**
     * Compact item editors are full-canvas screens: their content is written straight
     * into the inventory and their controls follow it in the last row. Content and
     * controls must not overlap, and both must fit the editor's row count.
     */
    private static void checkCompactEditorRows() {
        assertEqual("ROWS_EDITOR_KIT", GuiLayouts.ROWS_EDITOR_KIT, 5);
        assertEqual("ROWS_EDITOR_EC", GuiLayouts.ROWS_EDITOR_EC, 4);

        int kitLastRowStart = (GuiLayouts.ROWS_EDITOR_KIT - 1) * GuiLayouts.COLUMNS;
        int ecLastRowStart = (GuiLayouts.ROWS_EDITOR_EC - 1) * GuiLayouts.COLUMNS;

        assertEqual("KIT_CONTENT_START", GuiLayouts.KIT_CONTENT_START, 0);
        assertEqual("KIT_CONTENT_END", GuiLayouts.KIT_CONTENT_END, GuiLayouts.EDITOR_ARMOR_SLOT_END);
        assertEqual("EDITOR_KIT_CONTROL_START", GuiLayouts.EDITOR_KIT_CONTROL_START, GuiLayouts.KIT_CONTENT_END);
        assertEqual("EDITOR_KIT_CONTROL_START (column)", GuiLayouts.EDITOR_KIT_CONTROL_START,
                kitLastRowStart + GuiLayouts.EDITOR_DIVIDER_FIRST_COL);
        assertEqual("EDITOR_KIT_BACK_SLOT", GuiLayouts.EDITOR_KIT_BACK_SLOT,
                GuiLayouts.ROWS_EDITOR_KIT * GuiLayouts.COLUMNS - 1);

        assertEqual("EC_CONTENT_START", GuiLayouts.EC_CONTENT_START, 0);
        assertEqual("EC_CONTENT_END", GuiLayouts.EC_CONTENT_END, ecLastRowStart);
        assertEqual("EDITOR_EC_CONTROL_START", GuiLayouts.EDITOR_EC_CONTROL_START, GuiLayouts.EC_CONTENT_END);
        assertEqual("EDITOR_EC_CONTROL_START (row)", GuiLayouts.EDITOR_EC_CONTROL_START, ecLastRowStart);
        assertEqual("EDITOR_EC_BACK_SLOT", GuiLayouts.EDITOR_EC_BACK_SLOT,
                GuiLayouts.EDITOR_EC_CONTROL_START + 3);
        if (GuiLayouts.EDITOR_EC_BACK_SLOT >= GuiLayouts.ROWS_EDITOR_EC * GuiLayouts.COLUMNS) {
            throw new IllegalStateException("EDITOR_EC_BACK_SLOT is outside the EC editor");
        }

        if (GuiLayouts.KIT_CONTENT_END > GuiLayouts.EDITOR_KIT_CONTROL_START
                || GuiLayouts.EC_CONTENT_END > GuiLayouts.EDITOR_EC_CONTROL_START) {
            throw new IllegalStateException("Editor content overlaps its controls");
        }
    }

    private static void checkKitRoomRows() {
        // The kit-room editor's content stops exactly where its footer row starts.
        assertEqual("KIT_ROOM_CONTENT_START", GuiLayouts.KIT_ROOM_CONTENT_START, 0);
        assertEqual("KIT_ROOM_CONTENT_END", GuiLayouts.KIT_ROOM_CONTENT_END,
                GuiLayouts.EDITOR_FOOTER_ROW * GuiLayouts.COLUMNS);

        // The kit-room page list shows 18 pages on rows 1-2, so it needs a top
        // frame, two content rows, a footer and a bottom frame.
        assertEqual("KIT_ROOM_PAGES_PER_SCREEN", GuiLayouts.KIT_ROOM_PAGES_PER_SCREEN,
                2 * GuiLayouts.COLUMNS);
    }

    /**
     * The preview is a full canvas: the kit's own items occupy rows 0-3 plus
     * the armor in row 4, and the chrome gets what is left of row 4. If the
     * filler ever reaches into the armor, a redraw wipes it.
     */
    private static void checkPreviewRows() {
        int lastRow = GuiLayouts.ROWS_PREVIEW - 1;
        assertEqual("PREVIEW_BACK_SLOT", GuiLayouts.PREVIEW_BACK_SLOT,
                lastRow * GuiLayouts.COLUMNS + GuiLayouts.COLUMNS - 2);
        assertEqual("PREVIEW_LOAD_SLOT", GuiLayouts.PREVIEW_LOAD_SLOT,
                lastRow * GuiLayouts.COLUMNS + GuiLayouts.COLUMNS - 1);

        // The armor fills row 4 cols 0-4, so the filler may only start after it.
        assertEqual("PREVIEW_FILLER_FIRST_COL", GuiLayouts.PREVIEW_FILLER_FIRST_COL,
                GuiLayouts.EDITOR_ARMOR_SLOT_END - GuiLayouts.EDITOR_ARMOR_SLOT_START);
        // ...and it has to stop before Back.
        assertEqual("PREVIEW_FILLER_LAST_COL", GuiLayouts.PREVIEW_FILLER_LAST_COL,
                GuiLayouts.PREVIEW_BACK_SLOT % GuiLayouts.COLUMNS - 1);

        // Every free cell between the armor and Back must be accounted for.
        int fillerCells = GuiLayouts.PREVIEW_FILLER_LAST_COL - GuiLayouts.PREVIEW_FILLER_FIRST_COL + 1;
        int freeCells = GuiLayouts.PREVIEW_BACK_SLOT - GuiLayouts.KIT_CONTENT_END;
        if (fillerCells != freeCells) {
            throw new IllegalStateException(
                    "The preview paints " + fillerCells + " filler cells but has " + freeCells
                            + " free cells between the armor and Back");
        }

        // The preview's content must end before its chrome starts.
        if (GuiLayouts.KIT_CONTENT_END > GuiLayouts.PREVIEW_BACK_SLOT) {
            throw new IllegalStateException(
                    "KIT_CONTENT_END (" + GuiLayouts.KIT_CONTENT_END
                            + ") overlaps the preview's chrome, which starts at slot "
                            + GuiLayouts.PREVIEW_BACK_SLOT);
        }
    }

    /**
     * Bordered navigation screens keep content under the top glass row and above
     * the dock. Settings capacity is still measured in content rows only.
     */
    // ------------------------------------------------------------------ footers
    private static void checkRowCounts() {
        assertPositive("ROWS_CONFIRM", GuiLayouts.ROWS_CONFIRM);
        assertPositive("ROWS_EDITOR", GuiLayouts.ROWS_EDITOR);
        assertPositive("ROWS_PREVIEW", GuiLayouts.ROWS_PREVIEW);
        assertAtLeast("ROWS_CONFIRM", GuiLayouts.ROWS_CONFIRM, 3);
        assertAtLeast("ROWS_PREVIEW", GuiLayouts.ROWS_PREVIEW, 3);
        assertAtLeast("FOOTER_SAVE_COL", GuiLayouts.FOOTER_SAVE_COL, 0);
        assertEqual("BORDER_CONTENT_START", GuiLayouts.BORDER_CONTENT_START, 1);
    }

    /**
     * The settings screens hold whole rows of entries. If the per-screen count
     * and the row count drift apart, entries fall off the bottom of the screen
     * and the pager shows an empty page.
     */
    private static void checkSettingsCapacity() {
        // Dock screens: the hub holds 2 rows of groups, group and list screens 3 rows.
        assertEqual("GROUPS_PER_SCREEN", GuiSettingsSchema.GROUPS_PER_SCREEN, 2 * GuiLayouts.COLUMNS);
        assertEqual("SETTINGS_PER_SCREEN", GuiSettingsSchema.SETTINGS_PER_SCREEN, 3 * GuiLayouts.COLUMNS);
        assertEqual("ENTRIES_PER_SCREEN", GuiSettingsSchema.ENTRIES_PER_SCREEN, 3 * GuiLayouts.COLUMNS);
    }

    /** Bordered dock screens are "content rows + 2": top frame and dock bookend the content. */
    private static void checkDockRows() {
        int[] dash = {GuiLayouts.DASH_KITS_ROW, GuiLayouts.DASH_ENDERCHESTS_ROW,
                GuiLayouts.DASH_ACTIONS_ROW, GuiLayouts.DASH_DOCK_ROW};
        assertDistinctAscending("dashboard", dash);
        assertEqual("DASH_KITS_ROW", GuiLayouts.DASH_KITS_ROW, GuiLayouts.BORDER_CONTENT_START);
        assertEqual("DASH_DOCK_ROW", GuiLayouts.DASH_DOCK_ROW, GuiLayouts.ROWS_DASHBOARD - 1);
        assertEqual("ROWS_DASHBOARD", GuiLayouts.ROWS_DASHBOARD, GuiLayouts.rowsFor(3));
        assertEqual("ROWS_DOCK_TOOLS", GuiLayouts.ROWS_DOCK_TOOLS, GuiLayouts.rowsFor(3));
        assertEqual("DOCK_TOOLS_ROW", GuiLayouts.DOCK_TOOLS_ROW, GuiLayouts.contentRow(1));
        assertEqual("ROWS_DOCK_LIST18", GuiLayouts.ROWS_DOCK_LIST18, GuiLayouts.rowsFor(2));
        assertEqual("ROWS_DOCK_LIST27", GuiLayouts.ROWS_DOCK_LIST27, GuiLayouts.rowsFor(3));
        assertEqual("ROWS_PREMADE_DOCK", GuiLayouts.ROWS_PREMADE_DOCK, GuiLayouts.rowsFor(3));
        assertEqual("TABS_ROW", GuiLayouts.TABS_ROW, GuiLayouts.BORDER_CONTENT_START);
        assertEqual("PREMADE_CONTENT_ROW_ONE", GuiLayouts.PREMADE_CONTENT_ROW_ONE, GuiLayouts.contentRow(1));
        assertEqual("ROWS_CONFIRM", GuiLayouts.ROWS_CONFIRM, GuiLayouts.rowsFor(2));
        assertEqual("DOCK pager columns", GuiDock.COL_NEXT - GuiDock.COL_PREV, 2);
        assertEqual("DOCK page column", GuiDock.COL_PAGE, (GuiDock.COL_PREV + GuiDock.COL_NEXT) / 2);
    }

    // ------------------------------------------------------------------ helpers

    private static void assertDistinctAscending(String name, int[] values) {
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                if (values[i] == values[j]) {
                    throw new IllegalStateException(
                            name + " layout has duplicate row " + values[i]
                                    + " at indices " + i + " and " + j);
                }
            }
        }
        for (int i = 1; i < values.length; i++) {
            if (values[i] <= values[i - 1]) {
                throw new IllegalStateException(
                        name + " rows must be ascending; " + values[i - 1]
                                + " then " + values[i]);
            }
        }
    }

    private static void assertEqual(String name, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "GuiLayouts." + name + " is " + actual + ", expected " + expected);
        }
    }

    private static void assertAtLeast(String name, int value, int minimum) {
        if (value < minimum) {
            throw new IllegalStateException(
                    "GuiLayouts." + name + " is " + value + ", expected at least " + minimum);
        }
    }

    private static void assertPositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalStateException("GuiLayouts." + name + " must be positive; got " + value);
        }
    }
}
