package org.pvpcore.gui.kits.editor;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemEditorActionsTest {
    @Test
    void shiftClickOnlyCustomizesContentSlots() {
        assertTrue(ItemEditorActions.customize(ClickType.SHIFT_LEFT, 10, 0, 41));
        assertTrue(ItemEditorActions.customize(ClickType.SHIFT_RIGHT, 0, 0, 41));
        // The exclusive end is a control slot, not content.
        assertFalse(ItemEditorActions.customize(ClickType.SHIFT_LEFT, 41, 0, 41));
        assertFalse(ItemEditorActions.customize(ClickType.SHIFT_LEFT, -1, 0, 41));
        assertFalse(ItemEditorActions.customize(ClickType.LEFT, 10, 0, 41));
    }

    @Test
    void copyRequiresEmptyCursor() {
        assertTrue(ItemEditorActions.copy(ClickType.RIGHT, 10, 0, 41, true));
        assertFalse(ItemEditorActions.copy(ClickType.RIGHT, 10, 0, 41, false));
        assertFalse(ItemEditorActions.copy(ClickType.RIGHT, 41, 0, 41, true));
        assertFalse(ItemEditorActions.copy(ClickType.LEFT, 10, 0, 41, true));
    }

    @Test
    void dropRemovesFullStack() {
        assertTrue(ItemEditorActions.discard(ClickType.DROP, 10, 0, 41));
        assertTrue(ItemEditorActions.discard(ClickType.CONTROL_DROP, 10, 0, 41));
        assertFalse(ItemEditorActions.discard(ClickType.DROP, 41, 0, 41));
        assertFalse(ItemEditorActions.discard(ClickType.LEFT, 10, 0, 41));
    }

    @Test
    void enderChestRangeUsesItsOwnExclusiveEnd() {
        assertTrue(ItemEditorActions.customize(ClickType.SHIFT_LEFT, 26, 0, 27));
        assertFalse(ItemEditorActions.customize(ClickType.SHIFT_LEFT, 27, 0, 27));
        assertTrue(ItemEditorActions.copy(ClickType.RIGHT, 26, 0, 27, true));
        assertFalse(ItemEditorActions.copy(ClickType.RIGHT, 27, 0, 27, true));
        assertTrue(ItemEditorActions.discard(ClickType.CONTROL_DROP, 26, 0, 27));
        assertFalse(ItemEditorActions.discard(ClickType.CONTROL_DROP, 27, 0, 27));
    }
}
