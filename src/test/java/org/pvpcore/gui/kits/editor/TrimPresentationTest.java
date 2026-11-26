package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.cosmetics.CosmeticApplier;
import org.pvpcore.features.kits.cosmetics.CosmeticCatalog;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract for the redesigned trim browser: layout, previews, state, icons and empty page. */
class TrimPresentationTest {
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
    void layoutUsesSharedConstantsForEntriesPreviewAndNav() throws Exception {
        TrimEditorPage page = page(session(Material.DIAMOND_CHESTPLATE, 4), 4, 3);
        assertEquals(GuiLayouts.COSMETIC_BROWSER_ROWS, page.rows());
        assertEquals(GuiLayouts.COSMETIC_BROWSER_SIZE, page.topSize());
        assertEquals(GuiLayouts.COSMETIC_BROWSER_ENTRIES_PER_PAGE, TrimEditorPage.ENTRIES_PER_PAGE);
        assertEquals(18, TrimEditorPage.ENTRIES_PER_PAGE);
        assertEquals(0, TrimEditorPage.entrySlots().getFirst());
        assertEquals(17, TrimEditorPage.entrySlots().getLast());
        assertEquals(GuiLayouts.COSMETIC_TRIM_MODE_SLOT, TrimEditorPage.MODE_SLOT);
        assertEquals(GuiLayouts.COSMETIC_TRIM_REMOVE_SLOT, TrimEditorPage.REMOVE_SLOT);
        assertEquals(GuiLayouts.COSMETIC_TRIM_BACK_SLOT, TrimEditorPage.BACK_SLOT);
    }

    @Test
    void titlesPinKitSlotForBothModes() throws Exception {
        KitsContext context = contextWithMessages("pvpcore.gui.trim-pattern-title",
                "<bold>Trim Pattern - Kit {slot}</bold>");
        String pattern = TrimEditorPage.renderedTitle(context, 3, false);
        assertTrue(pattern.contains("Kit 3"), pattern);
        assertFalse(pattern.contains("{slot}"), pattern);

        KitsContext materialContext = contextWithMessages("pvpcore.gui.trim-material-title",
                "<bold>Trim Material - Kit {slot}</bold>");
        String material = TrimEditorPage.renderedTitle(materialContext, 3, true);
        assertTrue(material.contains("Kit 3"), material);
        assertFalse(material.contains("{slot}"), material);
    }

    @Test
    void previewIsAStrippedCloneCarryingTheResolvedTrim() {
        ItemStack draft = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = draft.getItemMeta();
        meta.lore(List.of(Component.text("ordinary lore")));
        draft.setItemMeta(meta);
        EditorHintLore.apply(draft);
        CosmeticApplier applier = applier();

        ItemStack preview = TrimEditorPage.preview(draft, applier, TrimMaterial.DIAMOND, TrimPattern.COAST);
        assertNotNull(preview);
        assertFalse(EditorHintLore.has(preview));
        assertTrue(EditorHintLore.has(draft), "the draft is never stripped in place");
        assertNotNull(((ArmorMeta) preview.getItemMeta()).getTrim());
        assertEquals(TrimPattern.COAST, ((ArmorMeta) preview.getItemMeta()).getTrim().getPattern());
        assertEquals(1, preview.getItemMeta().lore().size());

        assertNull(TrimEditorPage.preview(null, applier, TrimMaterial.DIAMOND, TrimPattern.COAST));
        assertNull(TrimEditorPage.preview(new ItemStack(Material.AIR), applier,
                TrimMaterial.DIAMOND, TrimPattern.COAST));
    }

    @Test
    void selectingStagesImmediatelyAndUpdatesThePreview() throws Exception {
        CustomizeSession session = session(Material.DIAMOND_CHESTPLATE, 4);
        TrimEditorPage page = page(session, 4, 3);
        assertNull(trim(page.previewForTest()));

        page.selectResolved(viewer, TrimMaterial.DIAMOND, TrimPattern.COAST);

        assertTrue(session.isStaged(4), "a trim click stages immediately");
        assertTrue(page.hasSelectionForTest());
        ArmorTrim trim = trim(page.previewForTest());
        assertNotNull(trim);
        assertEquals(TrimPattern.COAST, trim.getPattern());
        assertFalse(EditorHintLore.has(page.previewForTest()));
    }

    @Test
    void removeClearsSelectionAndRebuildsThePreview() throws Exception {
        TrimEditorPage page = page(session(Material.DIAMOND_CHESTPLATE, 4), 4, 3);
        page.selectResolved(viewer, TrimMaterial.DIAMOND, TrimPattern.COAST);
        assertTrue(page.hasSelectionForTest());

        page.remove(viewer);

        assertFalse(page.hasSelectionForTest(), "Remove must not leave a stale Selected state");
        assertNull(trim(page.previewForTest()));
    }

    @Test
    void modeSwitchRepaintsModeCopyWithoutLosingThePreview() throws Exception {
        TrimEditorPage page = page(session(Material.DIAMOND_CHESTPLATE, 4), 4, 3);
        page.selectResolved(viewer, TrimMaterial.DIAMOND, TrimPattern.COAST);
        ItemStack before = page.previewForTest();

        page.switchMode();
        page.paintForTest();

        assertNotNull(page.previewForTest());
        assertEquals(before.getType(), page.previewForTest().getType());
        Map<Integer, GuiItem> content = page.paintedContent();
        assertTrue(lore(content.get(GuiLayouts.COSMETIC_BROWSER_HINT_SLOT))
                .contains("Choosing material"), lore(content.get(GuiLayouts.COSMETIC_BROWSER_HINT_SLOT)));
        GuiItem preview = content.get(GuiLayouts.COSMETIC_BROWSER_PREVIEW_SLOT);
        assertNotNull(preview);
        assertDoesNotThrow(() -> preview.callAction(null), "preview must not have a click handler");
    }

    @Test
    void selectedIconReusesCatalogMappingWithoutGlow() {
        CosmeticCatalog.Entry entry = new CosmeticCatalog.Entry("minecraft:bolt", "Bolt",
                new ItemStack(Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE));

        ItemStack selected = TrimEditorPage.displayIcon(entry, true, "Pattern template");
        assertEquals(Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, selected.getType());
        assertFalse(selected.getItemMeta().getItemFlags().contains(ItemFlag.HIDE_ENCHANTS),
                "glow would hide the item's metadata");
        assertTrue(loreOf(selected).contains("Selected"), loreOf(selected));

        ItemStack plain = TrimEditorPage.displayIcon(entry, false, "Pattern template");
        assertFalse(loreOf(plain).contains("Selected"));
    }

    @Test
    void emptyCatalogShowsAnEmptyCardAndHidesThePager() throws Exception {
        CustomizeRules denyAll = new CustomizeRules(true, false, false,
                CustomizeRules.DEFAULT_MAX_NAME_LENGTH, Set.of("minecraft:__none__"),
                Set.of("minecraft:__none__"));
        CosmeticCatalog empty = CosmeticCatalog.fromRegistries(denyAll);
        TrimEditorPage page = new TrimEditorPage(gui, viewer, session(Material.DIAMOND_CHESTPLATE, 4),
                4, 3, empty);
        page.paintForTest();

        Map<Integer, GuiItem> content = page.paintedContent();
        assertTrue(lore(content.get(4)).contains("No trims available"), lore(content.get(4)));
        for (int slot = 0; slot <= GuiLayouts.COSMETIC_BROWSER_ENTRY_LAST; slot++) {
            if (slot != 4) {
                assertNotNull(content.get(slot), "empty-state filler slot " + slot);
            }
        }
        Map<Integer, GuiItem> chrome = page.paintedChrome();
        assertFalse(chrome.containsKey(GuiLayouts.COSMETIC_TRIM_PREV_SLOT));
        assertFalse(chrome.containsKey(GuiLayouts.COSMETIC_TRIM_NEXT_SLOT));
        assertFalse(chrome.containsKey(GuiLayouts.COSMETIC_TRIM_PAGE_SLOT));
        assertTrue(chrome.containsKey(GuiLayouts.COSMETIC_TRIM_MODE_SLOT));
        assertTrue(chrome.containsKey(GuiLayouts.COSMETIC_TRIM_REMOVE_SLOT));
        assertTrue(chrome.containsKey(GuiLayouts.COSMETIC_TRIM_BACK_SLOT));
        assertTrue(lore(content.get(GuiLayouts.COSMETIC_BROWSER_HINT_SLOT)).contains("Page 1 of 1"));
    }

    @Test
    void pagerVisibilityFollowsTheEightEntryPageSize() {
        assertFalse(TrimEditorPage.showsPager(0));
        assertFalse(TrimEditorPage.showsPager(18));
        assertTrue(TrimEditorPage.showsPager(19));
    }

    private static CosmeticApplier applier() {
        CustomizeRules rules = CustomizeRules.parse(null);
        return new CosmeticApplier(rules, new CosmeticValidator(rules));
    }

    private static ArmorTrim trim(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof ArmorMeta armor)) return null;
        return armor.getTrim();
    }

    private static String loreOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lines = meta == null ? null : meta.lore();
        if (lines == null) return "";
        StringBuilder out = new StringBuilder();
        for (Component line : lines) {
            out.append(PLAIN.serialize(line)).append(" | ");
        }
        return out.toString();
    }

    private static String lore(GuiItem item) {
        return loreOf(item.getItem());
    }

    private static CustomizeSession session(Material material, int slot) {
        ItemStack[] kit = new ItemStack[41];
        kit[slot] = new ItemStack(material);
        CustomizeSession session = new CustomizeSession(kit);
        session.selectTarget(slot);
        return session;
    }

    private TrimEditorPage page(CustomizeSession session, int slot, int kitSlot) throws Exception {
        Constructor<TrimEditorPage> constructor = TrimEditorPage.class.getDeclaredConstructor(
                GUI.class, Player.class, CustomizeSession.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gui, viewer, session, slot, kitSlot);
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
