package org.pvpcore.gui.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiLayoutChecksTest {
    @Test
    void editorLayoutsFitTheirRows() {
        GuiLayoutChecks.verify();
        assertEquals(45, GuiLayouts.KIT_CONTENT_END + 4);
        // The EC editor keeps a full row of controls after its 27 content slots.
        assertEquals(36, GuiLayouts.EC_CONTENT_END + 9);
    }

    @Test
    void verifyPinsTheCosmeticThreeRowContracts() {
        GuiLayoutChecks.verify();

        assertEquals(3, GuiLayouts.COSMETIC_HUB_ROWS);
        assertEquals(27, GuiLayouts.COSMETIC_HUB_SIZE);
        assertEquals(GuiLayouts.COSMETIC_HUB_ROWS * GuiLayouts.COLUMNS, GuiLayouts.COSMETIC_HUB_SIZE);
        // Hub actions keep the centre-column pattern: row 1, columns 0/2/4/6/8.
        assertEquals(GuiLayouts.COLUMNS, GuiLayouts.COSMETIC_HUB_RENAME_SLOT);
        assertEquals(GuiLayouts.COSMETIC_HUB_RENAME_SLOT + 2, GuiLayouts.COSMETIC_HUB_ENCHANT_SLOT);
        assertEquals(GuiLayouts.COSMETIC_HUB_ENCHANT_SLOT + 2, GuiLayouts.COSMETIC_HUB_TRIM_SLOT);
        assertEquals(GuiLayouts.COSMETIC_HUB_TRIM_SLOT + 2, GuiLayouts.COSMETIC_HUB_APPLY_SLOT);
        assertEquals(GuiLayouts.COSMETIC_HUB_APPLY_SLOT + 2, GuiLayouts.COSMETIC_HUB_CANCEL_SLOT);

        assertEquals(3, GuiLayouts.COSMETIC_BROWSER_ROWS);
        assertEquals(27, GuiLayouts.COSMETIC_BROWSER_SIZE);
        assertEquals(18, GuiLayouts.COSMETIC_BROWSER_ENTRIES_PER_PAGE);
        assertEquals(0, GuiLayouts.COSMETIC_BROWSER_ENTRY_FIRST);
        assertEquals(GuiLayouts.COSMETIC_BROWSER_ENTRY_FIRST
                + GuiLayouts.COSMETIC_BROWSER_ENTRIES_PER_PAGE - 1, GuiLayouts.COSMETIC_BROWSER_ENTRY_LAST);
        assertTrue(GuiLayouts.COSMETIC_BROWSER_ENTRY_LAST < GuiLayouts.COSMETIC_BROWSER_PREVIEW_SLOT);
        assertEquals(GuiLayouts.COSMETIC_BROWSER_PREVIEW_SLOT + 1, GuiLayouts.COSMETIC_BROWSER_STATE_SLOT);
        assertEquals(GuiLayouts.COSMETIC_BROWSER_STATE_SLOT + 1, GuiLayouts.COSMETIC_BROWSER_HINT_SLOT);
    }

    @Test
    void editorContentAndControlsDoNotOverlap() {
        assertEquals(5, GuiLayouts.ROWS_EDITOR_KIT);
        assertEquals(4, GuiLayouts.ROWS_EDITOR_EC);

        assertEquals(0, GuiLayouts.KIT_CONTENT_START);
        assertEquals(41, GuiLayouts.KIT_CONTENT_END);
        assertEquals(41, GuiLayouts.EDITOR_KIT_CONTROL_START);
        assertEquals(44, GuiLayouts.EDITOR_KIT_BACK_SLOT);

        assertEquals(0, GuiLayouts.EC_CONTENT_START);
        assertEquals(27, GuiLayouts.EC_CONTENT_END);
        assertEquals(27, GuiLayouts.EDITOR_EC_CONTROL_START);
        assertEquals(30, GuiLayouts.EDITOR_EC_BACK_SLOT);

        assertTrue(GuiLayouts.KIT_CONTENT_END <= GuiLayouts.EDITOR_KIT_CONTROL_START);
        assertTrue(GuiLayouts.EC_CONTENT_END <= GuiLayouts.EDITOR_EC_CONTROL_START);
    }
}
