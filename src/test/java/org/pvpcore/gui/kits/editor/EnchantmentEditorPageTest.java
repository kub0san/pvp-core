package org.pvpcore.gui.kits.editor;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.configuration.file.YamlConfiguration;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.features.kits.cosmetics.EnchantmentCatalog;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.StyleManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void pageUsesThreeRowsAndEighteenEntrySlots() throws Exception {
        EnchantmentEditorPage page = page(session(Material.DIAMOND_SWORD, 0), 0, 1);
        assertNotNull(page);
        assertEquals(GuiLayouts.COSMETIC_BROWSER_ROWS, page.rows());
        assertEquals(GuiLayouts.COSMETIC_BROWSER_SIZE, page.topSize());
        assertEquals(18, EnchantmentEditorPage.ENTRIES_PER_PAGE);
        assertEquals(18, EnchantmentEditorPage.entrySlots().size());
        assertEquals(0, EnchantmentEditorPage.entrySlots().getFirst());
        assertEquals(17, EnchantmentEditorPage.entrySlots().getLast());
    }

    @Test
    void leftClickCyclesOneToMaxAndStaysAtMax() {
        int max = Enchantment.SHARPNESS.getMaxLevel();
        assertTrue(max > 1, "the registry must expose a multi-level enchantment");
        assertEquals(1, EnchantmentEditorPage.nextLevel(0, max));
        assertEquals(2, EnchantmentEditorPage.nextLevel(1, max));
        assertEquals(max, EnchantmentEditorPage.nextLevel(max, max));
    }

    @Test
    void requestedLevelIsReadFromTheDraftAtClickTime() {
        ItemStack draft = new ItemStack(Material.DIAMOND_SWORD);
        int max = Enchantment.SHARPNESS.getMaxLevel();
        assertEquals(1, EnchantmentEditorPage.requestedLevel(draft, Enchantment.SHARPNESS, ClickType.LEFT, max));

        draft.addEnchantment(Enchantment.SHARPNESS, 2);
        assertEquals(EnchantmentEditorPage.nextLevel(2, max),
                EnchantmentEditorPage.requestedLevel(draft, Enchantment.SHARPNESS, ClickType.LEFT, max));
        assertEquals(0, EnchantmentEditorPage.requestedLevel(draft, Enchantment.SHARPNESS, ClickType.RIGHT, max));
    }

    @Test
    void previewCloneStripsHintsAndLeavesTheDraftUntouched() {
        ItemStack draft = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = draft.getItemMeta();
        meta.lore(List.of(Component.text("kept lore")));
        draft.setItemMeta(meta);

        ItemStack hinted = draft.clone();
        EditorHintLore.apply(hinted);
        assertTrue(EditorHintLore.has(hinted));

        ItemStack preview = EnchantmentEditorPage.preview(hinted);
        assertNotNull(preview);
        assertFalse(EditorHintLore.has(preview));
        assertEquals(1, preview.getItemMeta().lore().size());
        assertEquals("kept lore", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(preview.getItemMeta().lore().getFirst()));
        assertTrue(EditorHintLore.has(hinted), "the draft is never stripped in place");
        assertNull(EnchantmentEditorPage.preview(null));
        assertNull(EnchantmentEditorPage.preview(new ItemStack(Material.AIR)));
    }

    @Test
    void pagerIsHiddenForEmptyAndSinglePageCatalogs() {
        assertFalse(EnchantmentEditorPage.showsPager(0));
        assertFalse(EnchantmentEditorPage.showsPager(18));
        assertTrue(EnchantmentEditorPage.showsPager(19));

        assertTrue(EnchantmentEditorPage.pageSlice(List.of(), 0).isEmpty());
        var entries = EnchantmentCatalog.entries();
        assertEquals(Math.min(18, entries.size()), EnchantmentEditorPage.pageSlice(entries, 0).size());
        assertEquals(Math.max(0, Math.min(18, entries.size() - 18)),
                EnchantmentEditorPage.pageSlice(entries, 1).size());
    }

    @Test
    void conflictBadgeMirrorsPolicyInsteadOfTheApiAlone() {
        assertEquals(EnchantmentEditorPage.Status.AVAILABLE, EnchantmentEditorPage.status(false, false, true));
        assertEquals(EnchantmentEditorPage.Status.CONFLICT_ALLOWED, EnchantmentEditorPage.status(true, true, true));
        assertEquals(EnchantmentEditorPage.Status.CONFLICT_BLOCKED, EnchantmentEditorPage.status(true, false, true));
        assertEquals(EnchantmentEditorPage.Status.AVAILABLE, EnchantmentEditorPage.status(true, false, false));
    }

    @Test
    void bookTargetsSkipCanEnchantButStillDetectStoredConflicts() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 1);
        assertTrue(EnchantmentEditorPage.apiConflict(sword, Enchantment.SMITE));
        assertFalse(EnchantmentEditorPage.apiConflict(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SMITE));

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        ((EnchantmentStorageMeta) meta).addStoredEnchant(Enchantment.SHARPNESS, 1, true);
        book.setItemMeta(meta);
        assertTrue(EnchantmentEditorPage.apiConflict(book, Enchantment.SMITE));
        assertTrue(EnchantmentEditorPage.canEnchant(book, Enchantment.SHARPNESS),
                "stored enchants skip canEnchantItem just like ItemFilter");
    }

    @Test
    void cancelRestoresTheEntrySnapshotAndBackKeepsStagedEdits() {
        CustomizeSession unstaged = session(Material.DIAMOND_SWORD, 3);
        unstaged.selectTarget(3);
        ItemStack original = unstaged.target();
        unstaged.stageTarget(new ItemStack(Material.IRON_SWORD));
        EnchantmentEditorPage.restoreOnCancel(unstaged, original, false);
        assertFalse(unstaged.isStaged(3));
        assertEquals(Material.DIAMOND_SWORD, unstaged.target().getType());

        CustomizeSession staged = session(Material.DIAMOND_SWORD, 3);
        staged.selectTarget(3);
        ItemStack entrySnapshot = new ItemStack(Material.IRON_SWORD);
        entrySnapshot.addEnchantment(Enchantment.UNBREAKING, 1);
        staged.stageTarget(entrySnapshot);
        staged.stageTarget(new ItemStack(Material.DIAMOND_SWORD));
        EnchantmentEditorPage.restoreOnCancel(staged, entrySnapshot, true);
        assertTrue(staged.isStaged(3), "Back-style navigation keeps the entry's staged edits");
        assertEquals(Material.IRON_SWORD, staged.target().getType());
        assertEquals(1, staged.target().getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    void titleRendersTheKitSlotInsteadOfThePlaceholder() throws Exception {
        GUI loaded = new GUI(MockBukkit.createMockPlugin(), contextWithMessages());
        Constructor<EnchantmentEditorPage> constructor = EnchantmentEditorPage.class.getDeclaredConstructor(
                GUI.class, Player.class, CustomizeSession.class, int.class, int.class);
        constructor.setAccessible(true);
        EnchantmentEditorPage page = constructor.newInstance(loaded, viewer, session(Material.DIAMOND_SWORD, 3), 3, 3);

        String title = page.title();
        assertTrue(title.contains("Kit 3"), title);
        assertFalse(title.contains("{slot}"), title);
    }

    /** Minimal context carrying the real message template, without loading the final plugin class. */
    private static KitsContext contextWithMessages() throws Exception {
        KitsContext context = (KitsContext) allocate(KitsContext.class);
        YamlConfiguration messages = new YamlConfiguration();
        messages.set("pvpcore.gui.enchant-title", "<dark_aqua>Enchant Item - Kit {slot}</dark_aqua>");
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
