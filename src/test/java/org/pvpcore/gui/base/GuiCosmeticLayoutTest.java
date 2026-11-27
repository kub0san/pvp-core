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
    void cosmeticThreeRowSlotsMatchTheSharedContract() {
        assertEquals(3, GuiLayouts.COSMETIC_HUB_ROWS);
        assertEquals(27, GuiLayouts.COSMETIC_HUB_SIZE);
        assertEquals(0, GuiLayouts.COSMETIC_HUB_INFO_SLOT);
        assertEquals(4, GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT);
        assertEquals(8, GuiLayouts.COSMETIC_HUB_STATE_SLOT);
        assertEquals(9, GuiLayouts.COSMETIC_HUB_RENAME_SLOT);
        assertEquals(11, GuiLayouts.COSMETIC_HUB_ENCHANT_SLOT);
        assertEquals(13, GuiLayouts.COSMETIC_HUB_TRIM_SLOT);
        assertEquals(15, GuiLayouts.COSMETIC_HUB_APPLY_SLOT);
        assertEquals(17, GuiLayouts.COSMETIC_HUB_CANCEL_SLOT);
        assertEquals(21, GuiLayouts.COSMETIC_HUB_HINT_SLOT);

        assertEquals(3, GuiLayouts.COSMETIC_BROWSER_ROWS);
        assertEquals(27, GuiLayouts.COSMETIC_BROWSER_SIZE);
        assertEquals(18, GuiLayouts.COSMETIC_BROWSER_ENTRIES_PER_PAGE);
        assertEquals(0, GuiLayouts.COSMETIC_BROWSER_ENTRY_FIRST);
        assertEquals(17, GuiLayouts.COSMETIC_BROWSER_ENTRY_LAST);
        assertEquals(18, GuiLayouts.COSMETIC_BROWSER_PREVIEW_SLOT);
        assertEquals(19, GuiLayouts.COSMETIC_BROWSER_STATE_SLOT);
        assertEquals(20, GuiLayouts.COSMETIC_BROWSER_HINT_SLOT);

        assertEquals(21, GuiLayouts.COSMETIC_ENCHANT_BACK_SLOT);
        assertEquals(22, GuiLayouts.COSMETIC_ENCHANT_PREV_SLOT);
        assertEquals(23, GuiLayouts.COSMETIC_ENCHANT_NEXT_SLOT);
        assertEquals(24, GuiLayouts.COSMETIC_ENCHANT_APPLY_SLOT);
        assertEquals(25, GuiLayouts.COSMETIC_ENCHANT_CANCEL_SLOT);
        assertEquals(26, GuiLayouts.COSMETIC_ENCHANT_PAGE_SLOT);

        assertEquals(21, GuiLayouts.COSMETIC_TRIM_MODE_SLOT);
        assertEquals(22, GuiLayouts.COSMETIC_TRIM_PREV_SLOT);
        assertEquals(23, GuiLayouts.COSMETIC_TRIM_NEXT_SLOT);
        assertEquals(24, GuiLayouts.COSMETIC_TRIM_PAGE_SLOT);
        assertEquals(25, GuiLayouts.COSMETIC_TRIM_REMOVE_SLOT);
        assertEquals(26, GuiLayouts.COSMETIC_TRIM_BACK_SLOT);

        int[] hub = {
                GuiLayouts.COSMETIC_HUB_INFO_SLOT, GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT,
                GuiLayouts.COSMETIC_HUB_STATE_SLOT, GuiLayouts.COSMETIC_HUB_RENAME_SLOT,
                GuiLayouts.COSMETIC_HUB_ENCHANT_SLOT, GuiLayouts.COSMETIC_HUB_TRIM_SLOT,
                GuiLayouts.COSMETIC_HUB_APPLY_SLOT, GuiLayouts.COSMETIC_HUB_CANCEL_SLOT,
                GuiLayouts.COSMETIC_HUB_HINT_SLOT
        };
        int[] enchant = browserSlots(new int[]{
                GuiLayouts.COSMETIC_ENCHANT_BACK_SLOT, GuiLayouts.COSMETIC_ENCHANT_PREV_SLOT,
                GuiLayouts.COSMETIC_ENCHANT_NEXT_SLOT, GuiLayouts.COSMETIC_ENCHANT_APPLY_SLOT,
                GuiLayouts.COSMETIC_ENCHANT_CANCEL_SLOT, GuiLayouts.COSMETIC_ENCHANT_PAGE_SLOT
        });
        int[] trim = browserSlots(new int[]{
                GuiLayouts.COSMETIC_TRIM_MODE_SLOT, GuiLayouts.COSMETIC_TRIM_PREV_SLOT,
                GuiLayouts.COSMETIC_TRIM_NEXT_SLOT, GuiLayouts.COSMETIC_TRIM_PAGE_SLOT,
                GuiLayouts.COSMETIC_TRIM_REMOVE_SLOT, GuiLayouts.COSMETIC_TRIM_BACK_SLOT
        });
        assertDistinctSlots(hub);
        assertDistinctSlots(enchant);
        assertDistinctSlots(trim);
        assertEquals(27, enchant.length);
        assertEquals(27, trim.length);
    }

    private static int[] browserSlots(int[] nav) {
        int[] slots = new int[GuiLayouts.COSMETIC_BROWSER_ENTRIES_PER_PAGE + 3 + nav.length];
        int index = 0;
        for (int slot = GuiLayouts.COSMETIC_BROWSER_ENTRY_FIRST;
             slot <= GuiLayouts.COSMETIC_BROWSER_ENTRY_LAST; slot++) {
            slots[index++] = slot;
        }
        slots[index++] = GuiLayouts.COSMETIC_BROWSER_PREVIEW_SLOT;
        slots[index++] = GuiLayouts.COSMETIC_BROWSER_STATE_SLOT;
        slots[index++] = GuiLayouts.COSMETIC_BROWSER_HINT_SLOT;
        System.arraycopy(nav, 0, slots, index, nav.length);
        return slots;
    }

    private static void assertDistinctSlots(int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            assertTrue(slots[i] >= 0 && slots[i] < 27, "slot out of range: " + slots[i]);
            for (int j = i + 1; j < slots.length; j++) {
                assertNotEquals(slots[i], slots[j], "duplicate slot: " + slots[i]);
            }
        }
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