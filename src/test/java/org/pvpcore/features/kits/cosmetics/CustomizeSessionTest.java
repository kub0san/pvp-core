package org.pvpcore.features.kits.cosmetics;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
