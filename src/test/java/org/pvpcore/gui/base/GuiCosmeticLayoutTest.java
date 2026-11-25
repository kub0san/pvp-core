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

    @Test
    void customizeFamilyTitlesAreConvertedBeforeGuiScreenDeserializesThem() {
        // GuiScreen.titleComponent() reads a legacy string, so a raw MiniMessage title shows
        // literal tags in-game. sectionTitle uses this conversion; pin that it strips them.
        String title = org.pvpcore.util.TextUtil.toLegacy(
                GuiTheme.ACCENT + "<bold>Customize Kit 1");
        assertFalse(title.contains("<"), title);
        net.kyori.adventure.text.Component component =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(title);
        assertEquals("Customize Kit 1",
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(component));
    }
}