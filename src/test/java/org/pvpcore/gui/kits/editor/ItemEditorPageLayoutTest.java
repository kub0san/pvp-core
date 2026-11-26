package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.StyleManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dimension contract for the compact item editors. The pages are built through reflection so
 * the test pins the real {@link ItemEditorPage} row/control contract without standing up a
 * fully configured kits GUI.
 */
class ItemEditorPageLayoutTest {
    private GUI gui;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        gui = new GUI(MockBukkit.createMockPlugin(), null);
    }

    @AfterEach
    void tearDown() {
        GUI.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void kitEditorUsesFiveRows() throws Exception {
        assertEquals(5, GuiLayouts.ROWS_EDITOR_KIT);
        assertEquals(41, GuiLayouts.EDITOR_KIT_CONTROL_START);
        assertEquals(44, GuiLayouts.EDITOR_KIT_BACK_SLOT);

        for (GUI.EditorType type : new GUI.EditorType[]{
                GUI.EditorType.KIT, GUI.EditorType.PUBLIC_KIT, GUI.EditorType.INSPECT_KIT}) {
            ItemEditorPage page = page(type);
            assertEquals(GuiLayouts.ROWS_EDITOR_KIT, page.rows(), type.name());
            assertEquals(45, page.topSize(), type.name());
            assertEquals(4, page.editorFooterRow(), type.name());
            assertEquals(GuiFrame.Density.EDITOR_ARMOR, page.density(), type.name());
        }
    }

    @Test
    void enderChestEditorUsesFourRows() throws Exception {
        assertEquals(4, GuiLayouts.ROWS_EDITOR_EC);
        assertEquals(27, GuiLayouts.EDITOR_EC_CONTROL_START);
        assertEquals(30, GuiLayouts.EDITOR_EC_BACK_SLOT);

        for (GUI.EditorType type : new GUI.EditorType[]{
                GUI.EditorType.ENDERCHEST, GUI.EditorType.INSPECT_ENDERCHEST}) {
            ItemEditorPage page = page(type);
            assertEquals(GuiLayouts.ROWS_EDITOR_EC, page.rows(), type.name());
            assertEquals(36, page.topSize(), type.name());
            assertEquals(3, page.editorFooterRow(), type.name());
            assertEquals(GuiFrame.Density.EDITOR_EC, page.density(), type.name());
        }
    }

    @Test
    void kitControlsFillTheLastRowWithoutACustomizeButton() throws Exception {
        EditorSpec spec = spec(GUI.EditorType.KIT);
        Method controls = ItemEditorPage.class.getDeclaredMethod(
                "kitControls", GUI.class, Player.class, int.class, KitsContext.class, EditorSpec.class);
        controls.setAccessible(true);
        controls.invoke(null, gui, null, 0, null, spec);

        assertOnlyButtons(spec, 41, 42, 43, 44);
        // Slot 41 is the Ender-chest switch; the old ARMOR_STAND "Customize" button is gone.
        assertEquals(Material.ENDER_CHEST, spec.button(41).item().getType());
    }

    @Test
    void enderChestControlsFillTheLastRow() throws Exception {
        EditorSpec spec = spec(GUI.EditorType.ENDERCHEST);
        Method controls = ItemEditorPage.class.getDeclaredMethod(
                "ecControls", GUI.class, Player.class, int.class, KitsContext.class, EditorSpec.class);
        controls.setAccessible(true);
        controls.invoke(null, gui, null, 0, null, spec);

        assertOnlyButtons(spec, 27, 28, 29, 30);
        assertEquals(Material.CHEST, spec.button(27).item().getType());
    }

    @Test
    void compactChromeNeverPaintsOverContent() throws Exception {
        Method reserved = ItemEditorPage.class.getDeclaredMethod(
                "reservedColumns", EditorSpec.class, int.class);
        reserved.setAccessible(true);

        // The kit editor's armor shares row 4 with its controls; those columns are content.
        boolean[] kit = (boolean[]) reserved.invoke(null, spec(GUI.EditorType.KIT), 4);
        for (int col = 0; col < 5; col++) {
            assertTrue(kit[col], "armor column " + col + " must not receive chrome filler");
        }
        for (int col = 5; col < GuiLayouts.COLUMNS; col++) {
            assertFalse(kit[col], "control column " + col + " may receive chrome filler");
        }

        // The EC editor's last row is entirely controls.
        boolean[] ec = (boolean[]) reserved.invoke(null, spec(GUI.EditorType.ENDERCHEST), 3);
        for (boolean column : ec) {
            assertFalse(column, "no EC control column is content");
        }
    }

    @Test
    void compactFramesDoNotPaintDividerRows() {
        StaticPane kitFrame = new StaticPane(GuiLayouts.COLUMNS, GuiLayouts.ROWS_EDITOR_KIT);
        GuiFrame.paint(kitFrame, GuiLayouts.ROWS_EDITOR_KIT, GuiFrame.Density.EDITOR_ARMOR, (StyleManager) null);
        assertTrue(kitFrame.getItems().isEmpty(), "a five-row kit editor has no divider row to paint");

        StaticPane ecFrame = new StaticPane(GuiLayouts.COLUMNS, GuiLayouts.ROWS_EDITOR_EC);
        GuiFrame.paint(ecFrame, GuiLayouts.ROWS_EDITOR_EC, GuiFrame.Density.EDITOR_EC, (StyleManager) null);
        assertTrue(ecFrame.getItems().isEmpty(), "a four-row EC editor has no top frame or divider row");
    }

    private static void assertOnlyButtons(EditorSpec spec, int... expected) {
        for (int slot = 0; slot < GuiLayouts.MENU_SIZE; slot++) {
            boolean shouldHaveButton = false;
            for (int want : expected) {
                shouldHaveButton |= slot == want;
            }
            if (shouldHaveButton) {
                assertNotNull(spec.button(slot), "expected a control at slot " + slot);
            } else {
                assertNull(spec.button(slot), "no control expected at slot " + slot);
            }
        }
    }

    private ItemEditorPage page(GUI.EditorType type) throws Exception {
        Constructor<ItemEditorPage> constructor = ItemEditorPage.class.getDeclaredConstructor(
                GUI.class, Player.class, EditorSpec.class, GuiFrame.Density.class);
        constructor.setAccessible(true);
        GuiFrame.Density density = type.enderchest()
                ? GuiFrame.Density.EDITOR_EC : GuiFrame.Density.EDITOR_ARMOR;
        return constructor.newInstance(gui, null, spec(type), density);
    }

    private static EditorSpec spec(GUI.EditorType type) {
        int start = type.contentStart();
        int length = type.contentEnd() - start;
        return new EditorSpec("Editor", "perplayerkit.admin", null, start, length, true,
                new ItemStack[length], new GUI.EditorContext(type, 0, null, null, null));
    }
}
