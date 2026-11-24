package org.pvpcore.gui.base;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiCosmeticLayoutTest {
    @Test
    void cosmeticSlotsDoNotOverlap() {
        assertEquals(41, GuiLayouts.EDITOR_CUSTOMIZE_SLOT);
        assertEquals(9, GuiLayouts.CUSTOMIZE_PICKER_FIRST);
        assertTrue(GuiLayouts.CUSTOMIZE_PICKER_LAST < GuiLayouts.FOOTER_START);
        assertNotEquals(GuiLayouts.CUSTOMIZE_PREVIEW_SLOT, GuiLayouts.CUSTOMIZE_APPLY_SLOT);
        assertNotEquals(GuiLayouts.CUSTOMIZE_REMOVE_SLOT, GuiLayouts.CUSTOMIZE_APPLY_SLOT);
    }
}