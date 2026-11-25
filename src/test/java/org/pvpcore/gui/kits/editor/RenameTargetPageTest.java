package org.pvpcore.gui.kits.editor;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.kits.GUI;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure regression coverage for the rename cancel callback after a reload invalidates the screen. */
class RenameTargetPageTest {
    private ServerMock server;
    private GUI gui;
    private Player viewer;
    private RenameTargetPage page;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer();
        gui = new GUI(MockBukkit.createMockPlugin(), null);
        page = new RenameTargetPage(gui, viewer, new CustomizeSession(new ItemStack[41]), 1);
    }

    @AfterEach
    void tearDown() {
        GUI.shutdown();
        MockBukkit.unmock();
    }

    private static CustomizeSession session() {
        ItemStack[] kit = new ItemStack[41];
        kit[0] = new ItemStack(Material.DIAMOND_CHESTPLATE);
        CustomizeSession session = new CustomizeSession(kit);
        session.selectTarget(0);
        return session;
    }

    @Test
    void staleCancelDoesNotReopenAndDoesNotConsumePendingOperation() {
        UUID viewerId = viewer.getUniqueId();
        CustomizeSession held = session();
        UUID operationId = gui.holdPendingCustomize(viewerId, held);
        gui.markCustomizeStale(viewerId);

        assertFalse(page.cancelRename(viewer, operationId),
                "a stale cancel callback must not reopen the Customize screen");

        gui.clearCustomizeStale(viewerId);
        assertSame(held, gui.takePendingCustomize(viewerId, operationId),
                "a stale callback must not consume a still-valid pending operation");
    }

    @Test
    void validCancelReopensAndConsumesPendingOperation() {
        UUID viewerId = viewer.getUniqueId();
        CustomizeSession held = session();
        UUID operationId = gui.holdPendingCustomize(viewerId, held);
        gui.clearCustomizeStale(viewerId);

        assertTrue(page.cancelRename(viewer, operationId),
                "a valid cancel callback must reopen the Customize screen");

        assertNull(gui.takePendingCustomize(viewerId, operationId),
                "a valid cancel callback must consume the pending operation");
    }
}
