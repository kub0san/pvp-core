package org.pvpcore.features.kits.cosmetics;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizeSessionTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack[] source() {
        ItemStack[] kit = new ItemStack[41];
        kit[36] = new ItemStack(Material.DIAMOND_CHESTPLATE);
        kit[40] = new ItemStack(Material.SHIELD);
        return kit;
    }

    @Test
    void defensiveCopiesAndDiscard() {
        ItemStack[] source = source();
        CustomizeSession session = new CustomizeSession(source);
        session.selectTarget(36);
        ItemStack staged = new ItemStack(Material.NETHERITE_CHESTPLATE);
        session.stageTarget(staged);
        staged.setType(Material.IRON_CHESTPLATE);

        assertEquals(Material.DIAMOND_CHESTPLATE, source[36].getType());
        assertEquals(Material.NETHERITE_CHESTPLATE, session.target().getType());
        session.working()[36] = new ItemStack(Material.IRON_CHESTPLATE);
        assertEquals(Material.NETHERITE_CHESTPLATE, session.target().getType());

        session.discard();
        assertFalse(session.isStaged(36));
        assertEquals(Material.DIAMOND_CHESTPLATE, session.target().getType());
    }

    @Test
    void matchesCurrentComparesTheOriginalSnapshot() {
        CustomizeSession session = new CustomizeSession(source());
        assertTrue(session.matchesCurrent(source()));

        ItemStack[] changed = source();
        changed[40] = new ItemStack(Material.SHIELD, 2);
        assertFalse(session.matchesCurrent(changed));
        assertFalse(session.matchesCurrent(null));
        assertFalse(session.matchesCurrent(new ItemStack[40]));
    }

    @Test
    void resetTargetRestoresOnlyTheSelectedSlot() {
        ItemStack[] source = source();
        CustomizeSession session = new CustomizeSession(source);
        session.selectTarget(36);
        session.stageTarget(new ItemStack(Material.NETHERITE_CHESTPLATE));
        assertTrue(session.isStaged(36));

        session.resetTarget();

        assertFalse(session.isStaged(36));
        assertEquals(Material.DIAMOND_CHESTPLATE, session.target().getType());
        assertEquals(Material.SHIELD, session.working()[40].getType());
    }

    @Test
    void discardRestoresEveryStagedSlot() {
        CustomizeSession session = new CustomizeSession(source());
        session.selectTarget(36);
        session.stageTarget(new ItemStack(Material.NETHERITE_CHESTPLATE));
        session.selectTarget(40);
        session.stageTarget(new ItemStack(Material.IRON_BLOCK));

        session.discard();

        assertFalse(session.isStaged(36));
        assertFalse(session.isStaged(40));
        ItemStack[] working = session.working();
        assertEquals(Material.DIAMOND_CHESTPLATE, working[36].getType());
        assertEquals(Material.SHIELD, working[40].getType());
    }

    @Test
    void nullTargetAndNullStageAreSafe() {
        CustomizeSession session = new CustomizeSession(source());
        assertNull(session.target());
        session.selectTarget(3);
        assertNull(session.target());
        session.stageTarget(null);
        assertTrue(session.isStaged(3));
        assertNull(session.target());
        session.resetTarget();
        assertFalse(session.isStaged(3));
    }

    @Test
    void emptyKitIsNormalizedToFortyOneEmptySlots() {
        for (ItemStack[] empty : new ItemStack[][]{null, new ItemStack[0]}) {
            CustomizeSession session = new CustomizeSession(empty);

            assertEquals(41, session.working().length);
            assertDoesNotThrow(() -> session.selectTarget(40)); // banner/offhand target
            assertNull(session.target());
            assertNull(session.working()[40]);
            assertTrue(session.matchesCurrent(new ItemStack[0])); // stale compare at Apply
            assertTrue(session.matchesCurrent(null));
        }
    }

    @Test
    void shortKitIsPaddedWithoutMutatingTheSource() {
        ItemStack[] source = new ItemStack[]{new ItemStack(Material.DIAMOND_CHESTPLATE)};
        CustomizeSession session = new CustomizeSession(source);

        assertEquals(41, session.working().length);
        assertEquals(1, source.length); // the caller's array is untouched
        session.selectTarget(0);
        assertEquals(Material.DIAMOND_CHESTPLATE, session.target().getType());
        assertNull(session.working()[40]);
        assertTrue(session.matchesCurrent(source)); // an equivalent short read is still current
    }

    @Test
    void openingEmptyKitScreensNeverMutatesSavedData() {
        ItemStack[] saved = new ItemStack[0];
        CustomizeSession session = new CustomizeSession(saved);

        session.selectTarget(40); // banner tab
        assertNull(session.target()); // designer refuses a non-shield without touching the kit
        session.selectTarget(0); // rename target row
        session.stageTarget(new ItemStack(Material.STONE));
        session.discard();

        assertEquals(0, saved.length);
        assertTrue(session.matchesCurrent(saved));
        assertNull(session.working()[0]);
    }

    @Test
    void rebaseFromCachedContentsMatchesCurrentAndDropsStagedFlags() {
        CustomizeSession staged = new CustomizeSession(source());
        staged.selectTarget(36);
        staged.stageTarget(new ItemStack(Material.NETHERITE_CHESTPLATE));
        assertTrue(staged.isStaged(36));

        ItemStack[] cached = staged.working(); // eager cache mutation before the write resolves
        CustomizeSession rebased = staged.rebaseFrom(cached);

        assertNotSame(staged, rebased);
        assertTrue(rebased.matchesCurrent(cached));
        assertEquals(36, rebased.targetSlot());
        assertEquals(Material.NETHERITE_CHESTPLATE, rebased.target().getType());
        assertFalse(rebased.isStaged(36));
    }

    @Test
    void rebaseFromOutOfRangeTargetIsNotPreserved() {
        ItemStack[] longKit = new ItemStack[60];
        longKit[50] = new ItemStack(Material.SHIELD);
        CustomizeSession staged = new CustomizeSession(longKit);
        staged.selectTarget(50);

        CustomizeSession rebased = staged.rebaseFrom(source()); // 41 slots after normalization

        assertEquals(-1, rebased.targetSlot());
        assertTrue(rebased.matchesCurrent(source()));
    }

    @Test
    void rebaseFromNullCacheKeepsTakenSession() {
        CustomizeSession staged = new CustomizeSession(source());
        staged.selectTarget(36);
        staged.stageTarget(new ItemStack(Material.NETHERITE_CHESTPLATE));

        assertSame(staged, staged.rebaseFrom(null));
        assertEquals(Material.NETHERITE_CHESTPLATE, staged.target().getType());
        assertTrue(staged.isStaged(36));
    }
}
