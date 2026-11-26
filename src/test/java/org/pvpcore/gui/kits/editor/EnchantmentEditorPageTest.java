package org.pvpcore.gui.kits.editor;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.kits.GUI;

import java.lang.reflect.Constructor;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract for the compact enchantment screen: paging, level cycling and safe staging. */
class EnchantmentEditorPageTest {
    private ServerMock server;
    private GUI gui;
    private Player viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer();
        gui = new GUI(MockBukkit.createMockPlugin(), null);
    }

    @AfterEach
    void tearDown() {
        GUI.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void pageUsesThreeRowsAndTwentyOneEntrySlots() throws Exception {
        EnchantmentEditorPage page = page(session(Material.DIAMOND_SWORD, 0), 0, 1);
        assertNotNull(page);
        assertEquals(3, page.rows());
        assertEquals(27, page.topSize());
        assertEquals(21, EnchantmentEditorPage.ENTRIES_PER_PAGE);
        assertEquals(21, EnchantmentEditorPage.entrySlots().size());
        assertEquals(0, EnchantmentEditorPage.entrySlots().get(0));
        assertEquals(20, EnchantmentEditorPage.entrySlots().get(20));
    }

    @Test
    void leftClickCyclesOneToMaxAndWrapsToNone() {
        int max = Enchantment.SHARPNESS.getMaxLevel();
        assertTrue(max > 1, "the registry must expose a multi-level enchantment");
        assertEquals(1, EnchantmentEditorPage.nextLevel(0, max));
        assertEquals(2, EnchantmentEditorPage.nextLevel(1, max));
        assertEquals(0, EnchantmentEditorPage.nextLevel(max, max));
    }

    @Test
    void levelEditsPreserveUnrelatedEnchantmentsAndTheOriginal() {
        ItemStack sword = enchantedSword();
        ItemStack removed = EnchantmentEditorPage.withLevel(sword, Enchantment.SHARPNESS, 0);
        assertEquals(0, removed.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(1, removed.getEnchantmentLevel(Enchantment.UNBREAKING));
        assertEquals(1, sword.getEnchantmentLevel(Enchantment.SHARPNESS), "the source item is never mutated");

        ItemStack bumped = EnchantmentEditorPage.withLevel(sword, Enchantment.SHARPNESS, 2);
        assertEquals(2, bumped.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(1, bumped.getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    void invalidCandidateIsNeverStaged() {
        CustomizeSession session = session(Material.DIAMOND_SWORD, 3);
        session.selectTarget(3);
        ItemStack candidate = new ItemStack(Material.DIAMOND_SWORD);
        candidate.addEnchantment(Enchantment.SHARPNESS, 1);

        Predicate<ItemStack> rejectAll = item -> false;
        assertFalse(EnchantmentEditorPage.stageIfAllowed(session, candidate, rejectAll));
        assertFalse(session.isStaged(3), "a rejected item must not reach the session");

        assertTrue(EnchantmentEditorPage.stageIfAllowed(session, candidate, item -> true));
        assertTrue(session.isStaged(3));
        assertEquals(1, session.target().getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    void nullCandidatesAreRejectedBeforeStaging() {
        CustomizeSession session = session(Material.DIAMOND_SWORD, 3);
        session.selectTarget(3);
        assertFalse(EnchantmentEditorPage.stageIfAllowed(session, null, item -> true));
        assertFalse(EnchantmentEditorPage.stageIfAllowed(session, new ItemStack(Material.AIR), item -> true));
        assertFalse(session.isStaged(3));
    }

    private static ItemStack enchantedSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 1);
        sword.addEnchantment(Enchantment.UNBREAKING, 1);
        return sword;
    }

    private static CustomizeSession session(Material material, int slot) {
        ItemStack[] kit = new ItemStack[41];
        kit[slot] = new ItemStack(material);
        return new CustomizeSession(kit);
    }

    private EnchantmentEditorPage page(CustomizeSession session, int slot, int kitSlot) throws Exception {
        Constructor<EnchantmentEditorPage> constructor = EnchantmentEditorPage.class.getDeclaredConstructor(
                GUI.class, Player.class, CustomizeSession.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gui, viewer, session, slot, kitSlot);
    }
}
