package org.pvpcore.mechanic.totemfixes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for COMBAT2-06: only the END phase of a quick-craft drag is counted. */
class QuickCraftButtonsTest {

    @Test
    void recognisesQuickCraftEndButtons() {
        assertTrue(QuickCraftButtons.isEnd(2));
        assertTrue(QuickCraftButtons.isEnd(6));
        assertTrue(QuickCraftButtons.isEnd(10));
    }

    @Test
    void ignoresOtherPhasesAndMouseButtons() {
        assertFalse(QuickCraftButtons.isEnd(0)); // start, left
        assertFalse(QuickCraftButtons.isEnd(1)); // add, left
        assertFalse(QuickCraftButtons.isEnd(4)); // start, right
        assertFalse(QuickCraftButtons.isEnd(5)); // add, right
        assertFalse(QuickCraftButtons.isEnd(8)); // start, middle
        assertFalse(QuickCraftButtons.isEnd(9)); // add, middle
    }
}
