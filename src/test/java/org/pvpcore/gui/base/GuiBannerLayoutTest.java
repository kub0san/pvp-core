package org.pvpcore.gui.base;

import org.junit.jupiter.api.Test;
import org.pvpcore.config.kits.CustomizeRules;
import static org.junit.jupiter.api.Assertions.*;

class GuiBannerLayoutTest {
    @Test
    void bannerDesignerSlotsStayInTheirRows() {
        assertEquals(27, GuiLayouts.BANNER_LAYERS_LAST - GuiLayouts.BANNER_LAYERS_FIRST + 1);
        assertTrue(GuiLayouts.BANNER_LAYERS_LAST < GuiLayouts.FOOTER_START);
        assertEquals(16, CustomizeRules.HARD_MAX_LAYERS);
    }
}