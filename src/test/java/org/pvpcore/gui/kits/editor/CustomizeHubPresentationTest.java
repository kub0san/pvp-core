package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.StyleManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract for the three-row cosmetic hub: showcase, cards, filler and the pinned action slots. */
class CustomizeHubPresentationTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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
    void titlePinsKitSlotThroughTheLangFactory() throws Exception {
        GUI loaded = new GUI(MockBukkit.createMockPlugin(),
                contextWithMessages("pvpcore.gui.customize-title", "<bold>Customize Item Kit {slot}</bold>"));
        CustomizePage page = page(loaded, session(Material.DIAMOND_SWORD, 0), 0, 3);

        String title = page.title();
        assertTrue(title.contains("Kit 3"), title);
        assertFalse(title.contains("{slot}"), title);
    }

    @Test
    void showcaseIsAStrippedDisplayCloneWithNoClickHandler() throws Exception {
        CustomizeSession session = session(Material.DIAMOND_CHESTPLATE, 7);
        session.selectTarget(7);
        ItemStack staged = session.target();
        ItemMeta meta = staged.getItemMeta();
        meta.displayName(Component.text("Keep me"));
        meta.lore(List.of(Component.text("ordinary lore")));
        staged.setItemMeta(meta);
        EditorHintLore.apply(staged);
        session.stageTarget(staged);

        CustomizePage page = page(session, 7, 3);
        page.paintForTest();
        GuiItem showcase = page.paintedContent().get(GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT);
        assertNotNull(showcase);
        ItemStack display = showcase.getItem();
        assertEquals(Material.DIAMOND_CHESTPLATE, display.getType());
        assertNotSame(session.target(), display);
        assertFalse(EditorHintLore.has(display), "hint lore must not leak into the showcase");
        assertEquals("Keep me", PLAIN.serialize(display.getItemMeta().displayName()));
        assertEquals(1, display.getItemMeta().lore().size());
        assertEquals("ordinary lore", PLAIN.serialize(display.getItemMeta().lore().getFirst()));
        assertTrue(EditorHintLore.has(session.target()), "the staged copy is never stripped");
        assertDoesNotThrow(() -> showcase.callAction(null), "showcase must not have a click handler");
    }

    @Test
    void showcaseReflectsStagedChangesOnReopen() throws Exception {
        CustomizeSession session = session(Material.DIAMOND_SWORD, 3);
        session.selectTarget(3);
        ItemStack original = session.target();
        ItemMeta originalMeta = original.getItemMeta();
        originalMeta.displayName(Component.text("Original Blade"));
        original.setItemMeta(originalMeta);
        session.stageTarget(original);

        CustomizePage first = page(session, 3, 5);
        first.paintForTest();
        assertEquals("Original Blade", plainName(first.paintedContent()
                .get(GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT).getItem()));

        ItemStack renamed = session.target();
        ItemMeta meta = renamed.getItemMeta();
        meta.displayName(Component.text("Renamed Blade"));
        renamed.setItemMeta(meta);
        session.stageTarget(renamed);

        CustomizePage reopened = page(session, 3, 5);
        reopened.paintForTest();
        assertEquals("Renamed Blade", plainName(reopened.paintedContent()
                .get(GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT).getItem()));
    }

    @Test
    void emptyTargetShowcaseIsSafeAndReadOnly() throws Exception {
        CustomizeSession session = new CustomizeSession(new ItemStack[41]);
        session.selectTarget(7);
        CustomizePage page = page(session, 7, 3);
        page.paintForTest();

        GuiItem showcase = page.paintedContent().get(GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT);
        assertNotNull(showcase);
        assertFalse(showcase.getItem().getType().isAir());
        assertDoesNotThrow(() -> showcase.callAction(null));
    }

    @Test
    void infoStateAndHintCardsUseTheSharedMessageKeys() throws Exception {
        CustomizeSession session = session(Material.DIAMOND_SWORD, 0);
        session.selectTarget(0);
        CustomizePage page = page(session, 0, 3);
        page.paintForTest();

        Map<Integer, GuiItem> content = page.paintedContent();
        String info = lore(content.get(GuiLayouts.COSMETIC_HUB_INFO_SLOT));
        assertTrue(info.contains("Kit 3"), info);
        assertTrue(info.contains("Slot 0"), info);
        assertTrue(lore(content.get(GuiLayouts.COSMETIC_HUB_STATE_SLOT)).contains("No staged changes"));
        assertTrue(lore(content.get(GuiLayouts.COSMETIC_HUB_HINT_SLOT))
                .contains("Back keeps staged edits"));

        session.stageTarget(new ItemStack(Material.IRON_SWORD));
        CustomizePage staged = page(session, 0, 3);
        staged.paintForTest();
        assertTrue(lore(staged.paintedContent().get(GuiLayouts.COSMETIC_HUB_STATE_SLOT))
                .contains("Staged changes"));
    }

    @Test
    void fillerAvoidsEveryReservedSlotAndActionSlotsStayPut() throws Exception {
        CustomizeSession armor = session(Material.DIAMOND_HELMET, 0);
        armor.selectTarget(0);
        CustomizePage page = page(armor, 0, 3);
        page.paintForTest();

        Set<Integer> reserved = Set.of(0, 4, 8, 9, 11, 13, 15, 17, 21);
        Map<Integer, GuiItem> frame = page.paintedFrame();
        for (int slot = 0; slot < GuiLayouts.COSMETIC_HUB_SIZE; slot++) {
            assertEquals(!reserved.contains(slot), frame.containsKey(slot), "filler slot " + slot);
        }
        Map<Integer, GuiItem> chrome = page.paintedChrome();
        for (int slot : List.of(GuiLayouts.COSMETIC_HUB_RENAME_SLOT,
                GuiLayouts.COSMETIC_HUB_ENCHANT_SLOT, GuiLayouts.COSMETIC_HUB_TRIM_SLOT,
                GuiLayouts.COSMETIC_HUB_APPLY_SLOT, GuiLayouts.COSMETIC_HUB_CANCEL_SLOT)) {
            assertTrue(chrome.containsKey(slot), "action slot " + slot);
        }
        Map<Integer, GuiItem> content = page.paintedContent();
        for (int slot : List.of(GuiLayouts.COSMETIC_HUB_INFO_SLOT,
                GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT, GuiLayouts.COSMETIC_HUB_STATE_SLOT,
                GuiLayouts.COSMETIC_HUB_HINT_SLOT)) {
            assertTrue(content.containsKey(slot), "card slot " + slot);
        }
    }

    private static CustomizeSession session(Material material, int slot) {
        ItemStack[] kit = new ItemStack[41];
        kit[slot] = new ItemStack(material);
        CustomizeSession session = new CustomizeSession(kit);
        session.selectTarget(slot);
        return session;
    }

    private CustomizePage page(CustomizeSession session, int slot, int kitSlot) throws Exception {
        return page(gui, session, slot, kitSlot);
    }

    private CustomizePage page(GUI owner, CustomizeSession session, int slot, int kitSlot) throws Exception {
        Constructor<CustomizePage> constructor = CustomizePage.class.getDeclaredConstructor(
                GUI.class, Player.class, CustomizeSession.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(owner, viewer, session, slot, kitSlot);
    }

    private static String plainName(ItemStack item) {
        return PLAIN.serialize(item.getItemMeta().displayName());
    }

    private static String lore(GuiItem item) {
        ItemMeta meta = item.getItem().getItemMeta();
        List<Component> lines = meta == null ? null : meta.lore();
        if (lines == null) return "";
        StringBuilder text = new StringBuilder();
        for (Component line : lines) {
            text.append(PLAIN.serialize(line)).append(" | ");
        }
        return text.toString();
    }

    /** Minimal context carrying the real message template, without loading the final plugin class. */
    private static KitsContext contextWithMessages(String key, String value) throws Exception {
        KitsContext context = (KitsContext) allocate(KitsContext.class);
        YamlConfiguration messages = new YamlConfiguration();
        messages.set(key, value);
        Constructor<Lang> langConstructor = Lang.class.getDeclaredConstructor(YamlConfiguration.class);
        langConstructor.setAccessible(true);
        Lang lang = langConstructor.newInstance(messages);
        context.setConfig(new YamlConfiguration());
        context.setLang(lang);
        context.setStyleManager(new StyleManager(context));
        return context;
    }

    private static Object allocate(Class<?> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return method.invoke(unsafe, type);
    }
}
