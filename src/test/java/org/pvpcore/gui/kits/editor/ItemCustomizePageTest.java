package org.pvpcore.gui.kits.editor;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the item-specific customization hub: one 27-slot screen, no 41-slot target
 * picker, and a rename prompt that cannot revive a session invalidated by a reload.
 */
class ItemCustomizePageTest {
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
    void hubOpensForSelectedSlotWithoutTargetPicker() throws Exception {
        CustomizeSession session = session(Material.DIAMOND_CHESTPLATE, 7);
        session.selectTarget(7);

        assertEquals(7, session.targetSlot());
        assertEquals(Material.DIAMOND_CHESTPLATE, session.target().getType());
        CustomizePage page = page(session, 7, 1);
        assertNotNull(page);
        assertEquals(3, page.rows());
        assertEquals(27, page.topSize());
    }

    @Test
    void hubActionsFitOneRow() {
        CustomizeSession session = session(Material.DIAMOND_SWORD, 0);
        session.selectTarget(0);
        List<CustomizePage.HubButton> buttons = CustomizePage.hubButtons(session);

        assertEquals(4, buttons.size(), "non-armor hub has Rename, Enchant, Apply and Cancel");
        assertEquals(List.of(0, 2, 6, 8), buttons.stream().map(CustomizePage.HubButton::column).toList());
        assertEquals(Material.NAME_TAG, buttons.get(0).icon());
        assertEquals(Material.ENCHANTED_BOOK, buttons.get(1).icon());
        assertEquals(Material.LIME_DYE, buttons.get(2).icon());
        assertEquals(Material.BARRIER, buttons.get(3).icon());
    }

    @Test
    void trimButtonOnlyForArmor() {
        assertTrue(CustomizePage.supportsTrim(new ItemStack(Material.DIAMOND_HELMET)));
        assertFalse(CustomizePage.supportsTrim(new ItemStack(Material.SHIELD)));
        assertFalse(CustomizePage.supportsTrim(new ItemStack(Material.DIAMOND_SWORD)));

        CustomizeSession armor = session(Material.DIAMOND_HELMET, 0);
        armor.selectTarget(0);
        List<CustomizePage.HubButton> armored = CustomizePage.hubButtons(armor);
        assertEquals(5, armored.size());
        assertEquals(4, armored.get(2).column());
        assertEquals(Material.NETHERITE_INGOT, armored.get(2).icon());

        CustomizeSession sword = session(Material.DIAMOND_SWORD, 0);
        sword.selectTarget(0);
        assertEquals(4, CustomizePage.hubButtons(sword).size());
    }

    @Test
    void renameStagesOnlyTheSelectedTarget() {
        CustomizeSession session = session(Material.DIAMOND_SWORD, 3);
        session.selectTarget(3);
        UUID operationId = gui.holdPendingCustomize(viewer.getUniqueId(), session);

        CustomizeSession staged = CustomizePage.stageRename(gui, viewer, operationId, "<red>Blade</red>");

        assertNotNull(staged);
        assertTrue(staged.isStaged(3));
        assertFalse(staged.isStaged(0));
        assertEquals("Blade", PlainTextComponentSerializer.plainText()
                .serialize(staged.target().getItemMeta().displayName()));
    }

    @Test
    void staleRenameCannotReviveSession() {
        CustomizeSession session = session(Material.DIAMOND_SWORD, 3);
        session.selectTarget(3);
        UUID operationId = gui.holdPendingCustomize(viewer.getUniqueId(), session);
        gui.markCustomizeStale(viewer.getUniqueId());

        assertNull(CustomizePage.stageRename(gui, viewer, operationId, "New name"));
        assertFalse(session.isStaged(3), "a reload invalidated the staged edit");
    }

    @Test
    void renameCancelConsumesPendingOnlyWhenValid() throws Exception {
        CustomizeSession session = session(Material.DIAMOND_SWORD, 3);
        session.selectTarget(3);
        CustomizePage page = page(session, 3, 1);
        UUID operationId = gui.holdPendingCustomize(viewer.getUniqueId(), session);
        gui.markCustomizeStale(viewer.getUniqueId());

        assertFalse(page.cancelRename(viewer, operationId), "a stale cancel must not reopen the hub");

        gui.clearCustomizeStale(viewer.getUniqueId());
        assertTrue(page.cancelRename(viewer, operationId));
        assertNull(gui.takePendingCustomize(viewer.getUniqueId(), operationId));
    }

    private static CustomizeSession session(Material material, int slot) {
        ItemStack[] kit = new ItemStack[41];
        kit[slot] = new ItemStack(material);
        return new CustomizeSession(kit);
    }

    private CustomizePage page(CustomizeSession session, int slot, int kitSlot) throws Exception {
        Constructor<CustomizePage> constructor = CustomizePage.class.getDeclaredConstructor(
                GUI.class, Player.class, CustomizeSession.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gui, viewer, session, slot, kitSlot);
    }
}
