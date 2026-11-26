package org.pvpcore.gui.kits.editor;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.cosmetics.CosmeticApplier;
import org.pvpcore.features.kits.cosmetics.CosmeticCatalog;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.kits.GUI;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract for the compact armor-only trim screen: gating, paging and safe staging. */
class TrimEditorPageTest {
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
    void trimButtonOnlyForArmor() {
        assertTrue(CustomizePage.supportsTrim(new ItemStack(Material.DIAMOND_HELMET)));
        assertTrue(CustomizePage.supportsTrim(new ItemStack(Material.NETHERITE_CHESTPLATE)));
        assertFalse(CustomizePage.supportsTrim(new ItemStack(Material.SHIELD)));
        assertFalse(CustomizePage.supportsTrim(new ItemStack(Material.DIAMOND_SWORD)));
        assertFalse(CustomizePage.supportsTrim(null));
    }

    @Test
    void pageUsesThreeRowsAndEighteenEntrySlots() throws Exception {
        TrimEditorPage page = page(session(Material.DIAMOND_HELMET, 4), 4, 1);
        assertNotNull(page);
        assertEquals(3, page.rows());
        assertEquals(27, page.topSize());
        assertEquals(18, TrimEditorPage.ENTRIES_PER_PAGE);
        assertEquals(18, TrimEditorPage.entrySlots().size());
        assertEquals(0, TrimEditorPage.entrySlots().get(0));
        assertEquals(17, TrimEditorPage.entrySlots().get(17));
    }

    @Test
    void nonArmorCandidateIsNeverStaged() {
        CustomizeSession session = session(Material.DIAMOND_SWORD, 3);
        session.selectTarget(3);

        assertFalse(TrimEditorPage.stageIfArmor(session, new ItemStack(Material.DIAMOND_SWORD)));
        assertFalse(TrimEditorPage.stageIfArmor(session, new ItemStack(Material.SHIELD)));
        assertFalse(session.isStaged(3), "a rejected item must not reach the session");

        assertTrue(TrimEditorPage.stageIfArmor(session, new ItemStack(Material.DIAMOND_HELMET)));
        assertTrue(session.isStaged(3));
    }

    @Test
    void missingRegistryObjectsAreRejectedBeforeApplying() {
        CustomizeRules rules = CustomizeRules.parse(null);
        CosmeticApplier applier = new CosmeticApplier(rules, new CosmeticValidator(rules));

        assertNull(TrimEditorPage.applyOrNull(
                new ItemStack(Material.DIAMOND_HELMET), applier, null, null));
        assertNull(TrimEditorPage.applyOrNull(
                new ItemStack(Material.DIAMOND_SWORD), applier, null, null));
        assertNull(TrimEditorPage.removeOrNull(new ItemStack(Material.DIAMOND_SWORD), applier));
        assertNull(CosmeticCatalog.trimPattern("pack:missing"));
        assertNull(CosmeticCatalog.trimMaterial("pack:missing"));
    }

    @Test
    void catalogTrimOptionsHaveNamesAndIcons() {
        CosmeticCatalog catalog = CosmeticCatalog.fromRegistries(CustomizeRules.parse(null));
        catalog.trimPatterns().forEach(entry -> {
            assertNotNull(entry.label());
            assertFalse(entry.icon().getType().isAir());
        });
        catalog.trimMaterials().forEach(entry -> {
            assertNotNull(entry.label());
            assertFalse(entry.icon().getType().isAir());
        });
    }

    private static CustomizeSession session(Material material, int slot) {
        ItemStack[] kit = new ItemStack[41];
        kit[slot] = new ItemStack(material);
        return new CustomizeSession(kit);
    }

    private TrimEditorPage page(CustomizeSession session, int slot, int kitSlot) throws Exception {
        Constructor<TrimEditorPage> constructor = TrimEditorPage.class.getDeclaredConstructor(
                GUI.class, Player.class, CustomizeSession.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gui, viewer, session, slot, kitSlot);
    }
}
