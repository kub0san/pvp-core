package org.pvpcore.gui.kits.page;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression for #30: the dashboard must paginate every configured kit slot (1..99),
 * not stop at 54. These are pure tests of the deterministic render-range seam; the
 * GUI layout itself stays a nine-card, five-row chest.
 */
class KitListPageTest {

    @Test
    void firstDashboardPageShowsSlotsOneThroughNine() {
        KitListPage.VisibleSlots slots = KitListPage.visibleSlots(0, 99);
        assertEquals(1, slots.first());
        assertEquals(9, slots.last());
    }

    @Test
    void sixthDashboardPageShowsSlotsFortySixThroughFiftyFour() {
        KitListPage.VisibleSlots slots = KitListPage.visibleSlots(5, 99);
        assertEquals(46, slots.first());
        assertEquals(54, slots.last());
    }

    @Test
    void seventhDashboardPageShowsPreviouslyUnreachableSlotsFiftyFiveThroughSixtyThree() {
        KitListPage.VisibleSlots slots = KitListPage.visibleSlots(6, 99);
        assertEquals(55, slots.first());
        assertEquals(63, slots.last());
    }

    @Test
    void lastDashboardPageShowsSlotsNinetyOneThroughNinetyNine() {
        KitListPage.VisibleSlots slots = KitListPage.visibleSlots(10, 99);
        assertEquals(91, slots.first());
        assertEquals(99, slots.last());
    }

    @Test
    void negativePageClampsToFirstPage() {
        KitListPage.VisibleSlots slots = KitListPage.visibleSlots(-3, 99);
        assertEquals(1, slots.first());
        assertEquals(9, slots.last());
    }

    @Test
    void pagePastLastClampsToLastPage() {
        KitListPage.VisibleSlots slots = KitListPage.visibleSlots(50, 99);
        assertEquals(91, slots.first());
        assertEquals(99, slots.last());
    }

    @Test
    void shortLastPageContainsOnlyItsRemainingSlot() {
        KitListPage.VisibleSlots slots = KitListPage.visibleSlots(1, 10);
        assertEquals(10, slots.first());
        assertEquals(10, slots.last());
    }

    @Test
    void zeroOrNegativeMaxKitsStillShowsExactlyOneSlot() {
        KitListPage.VisibleSlots zero = KitListPage.visibleSlots(0, 0);
        assertEquals(1, zero.first());
        assertEquals(1, zero.last());

        KitListPage.VisibleSlots negative = KitListPage.visibleSlots(0, -3);
        assertEquals(1, negative.first());
        assertEquals(1, negative.last());
    }

    @Test
    void allElevenPagesCoverSlotsOneToNinetyNineExactlyOnce() {
        List<Integer> seen = new ArrayList<>();
        for (int page = 0; page < 11; page++) {
            KitListPage.VisibleSlots slots = KitListPage.visibleSlots(page, 99);
            assertEquals(page * 9 + 1, slots.first());
            assertEquals(Math.min(99, slots.first() + 8), slots.last());
            for (int slot = slots.first(); slot <= slots.last(); slot++) {
                seen.add(slot);
            }
        }
        assertEquals(99, seen.size());
        for (int slot = 1; slot <= 99; slot++) {
            assertEquals(Integer.valueOf(slot), seen.get(slot - 1));
        }
    }
}
