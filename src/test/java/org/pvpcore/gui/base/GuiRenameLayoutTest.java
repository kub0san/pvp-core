package org.pvpcore.gui.base;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiRenameLayoutTest {
    @Test
    void renameTargetsCoverEntireKit() {
        assertEquals(0, GuiLayouts.RENAME_TARGET_FIRST);
        assertEquals(40, GuiLayouts.RENAME_TARGET_LAST);
        assertTrue(GuiLayouts.RENAME_TARGET_LAST < GuiLayouts.FOOTER_START);
    }
}