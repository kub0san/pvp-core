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
