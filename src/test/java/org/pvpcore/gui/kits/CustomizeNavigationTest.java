package org.pvpcore.gui.kits;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the close decision that protects staged edits during tab switches. */
class CustomizeNavigationTest {
    private ServerMock server;
    private GUI gui;
    private UUID viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer().getUniqueId();
        gui = new GUI(MockBukkit.createMockPlugin(), null);
    }

    @AfterEach
    void tearDown() {
        GUI.shutdown();
        MockBukkit.unmock();
    }

    private static CustomizeSession stagedSession() {
        ItemStack[] kit = new ItemStack[41];
        kit[0] = new ItemStack(Material.DIAMOND_CHESTPLATE);
        CustomizeSession session = new CustomizeSession(kit);
        session.selectTarget(0);
        session.stageTarget(new ItemStack(Material.NETHERITE_CHESTPLATE));
        return session;
    }

    @Test
    void tabSwitchKeepsStagedChangesBecauseIncomingScreenRegistersFirst() {
        CustomizeSession session = stagedSession();
        gui.registerCustomizeScreen(viewer); // CustomizePage
        gui.registerCustomizeScreen(viewer); // New hub/page opens before the old one closes

        gui.unregisterCustomizeScreen(viewer); // old CustomizePage onClose
        assertEquals(1, gui.customizeScreenCount(viewer));
        assertFalse(gui.isLastCustomizeScreen(viewer));
        if (gui.isLastCustomizeScreen(viewer)) session.discard(); // mirrors CustomizePage.onClose
        assertTrue(session.isStaged(0), "staged trim/rename must survive a tab switch");

        gui.unregisterCustomizeScreen(viewer); // genuine close of the remaining screen
        assertTrue(gui.isLastCustomizeScreen(viewer));
        if (gui.isLastCustomizeScreen(viewer)) session.discard();
        assertFalse(session.isStaged(0), "a real close must still discard staged edits");
        assertEquals(Material.DIAMOND_CHESTPLATE, session.target().getType());
    }

    @Test
    void singleScreenCloseAlwaysDiscards() {
        CustomizeSession session = stagedSession();
        gui.registerCustomizeScreen(viewer);
        assertEquals(1, gui.customizeScreenCount(viewer));

        gui.unregisterCustomizeScreen(viewer);
        assertTrue(gui.isLastCustomizeScreen(viewer));
        if (gui.isLastCustomizeScreen(viewer)) session.discard();

        assertFalse(session.isStaged(0));
        assertEquals(Material.DIAMOND_CHESTPLATE, session.target().getType());
    }
}
