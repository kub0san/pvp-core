package org.pvpcore.gui.base;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.pvpcore.gui.kits.editor.EditorHintLore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiPreviewItemsTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void nullAndAirReturnNull() {
        assertNull(GuiPreviewItems.displayClone(null));
        assertNull(GuiPreviewItems.displayClone(new ItemStack(Material.AIR)));
    }

    @Test
    void returnsNewClonePreservingNameLoreEnchantsAndPdc() {
        ItemStack source = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = source.getItemMeta();
        meta.setDisplayName("Blade");
        meta.setLore(List.of("Line one", "Line two"));
        meta.addEnchant(Enchantment.SHARPNESS, 3, true);
        NamespacedKey key = new NamespacedKey("pvp-core", "preview_test");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 7);
        source.setItemMeta(meta);

        ItemStack clone = GuiPreviewItems.displayClone(source);
        ItemMeta copy = clone.getItemMeta();

        assertNotSame(source, clone);
        assertEquals("Blade", copy.getDisplayName());
        assertEquals(List.of("Line one", "Line two"), copy.getLore());
        assertEquals(3, source.getEnchantments().get(Enchantment.SHARPNESS));
        assertEquals(3, clone.getEnchantments().get(Enchantment.SHARPNESS));
        assertEquals((byte) 7, copy.getPersistentDataContainer().get(key, PersistentDataType.BYTE));
        assertEquals("Blade", source.getItemMeta().getDisplayName());
    }

    @Test
    void clonePreservesArmorTrim() {
        ItemStack source = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ArmorMeta meta = (ArmorMeta) source.getItemMeta();
        meta.setTrim(new ArmorTrim(TrimMaterial.DIAMOND, TrimPattern.COAST));
        source.setItemMeta(meta);

        ItemStack clone = GuiPreviewItems.displayClone(source);
        ArmorTrim trim = ((ArmorMeta) clone.getItemMeta()).getTrim();

        assertNotSame(source, clone);
        assertNotNull(trim);
        assertEquals(TrimMaterial.DIAMOND, trim.getMaterial());
        assertEquals(TrimPattern.COAST, trim.getPattern());
        assertTrue(((ArmorMeta) source.getItemMeta()).hasTrim());
    }

    @Test
    void stripsHintLoreOnTheCloneOnly() {
        ItemStack source = named("Blade", "Original lore");
        ItemStack staged = EditorHintLore.apply(source.clone());
        assertTrue(EditorHintLore.has(staged));

        ItemStack display = GuiPreviewItems.displayClone(staged);

        assertNotSame(staged, display);
        assertFalse(EditorHintLore.has(display));
        assertEquals(List.of("Original lore"), display.getItemMeta().getLore());
        assertEquals("Blade", display.getItemMeta().getDisplayName());
        // The staged item and the source are never modified.
        assertTrue(EditorHintLore.has(staged));
        assertEquals(4, staged.getItemMeta().lore().size());
        assertFalse(EditorHintLore.has(source));
        assertEquals(List.of("Original lore"), source.getItemMeta().getLore());
    }

    @Test
    void stripsTrailingHintLoreWhileOrdinaryMetadataSurvives() {
        ItemStack source = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = source.getItemMeta();
        meta.setDisplayName("Kept name");
        meta.setLore(List.of("Kept lore"));
        meta.addEnchant(Enchantment.PROTECTION, 2, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        NamespacedKey keptKey = new NamespacedKey("pvp-core", "kept_key");
        meta.getPersistentDataContainer().set(keptKey, PersistentDataType.STRING, "kept");
        source.setItemMeta(meta);
        ArmorMeta armor = (ArmorMeta) source.getItemMeta();
        armor.setTrim(new ArmorTrim(TrimMaterial.DIAMOND, TrimPattern.COAST));
        source.setItemMeta(armor);

        // Caller rule: hints are applied last, so strip removes exactly that trailing block.
        ItemStack staged = EditorHintLore.apply(source.clone());
        assertTrue(EditorHintLore.has(staged));

        ItemStack display = GuiPreviewItems.displayClone(staged);
        ItemMeta copy = display.getItemMeta();

        assertFalse(EditorHintLore.has(display));
        assertEquals("Kept name", copy.getDisplayName());
        assertEquals(List.of("Kept lore"), copy.getLore());
        assertEquals(2, copy.getEnchants().get(Enchantment.PROTECTION));
        assertEquals("kept", copy.getPersistentDataContainer().get(keptKey, PersistentDataType.STRING));
        assertEquals(java.util.Set.of(ItemFlag.HIDE_ATTRIBUTES), copy.getItemFlags());
        ArmorTrim trim = ((ArmorMeta) display.getItemMeta()).getTrim();
        assertNotNull(trim);
        assertEquals(TrimMaterial.DIAMOND, trim.getMaterial());
        assertEquals(TrimPattern.COAST, trim.getPattern());

        // Neither the staged input nor the source is modified by the display clone.
        assertTrue(EditorHintLore.has(staged));
        assertEquals(4, staged.getItemMeta().lore().size());
        assertFalse(EditorHintLore.has(source));
        assertEquals(List.of("Kept lore"), source.getItemMeta().getLore());
    }

    @Test
    void addsNoFlagsLoreEnchantsOrPdcKeysOfItsOwn() {
        ItemStack source = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta meta = source.getItemMeta();
        meta.setDisplayName("Plain");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        source.setItemMeta(meta);

        ItemStack clone = GuiPreviewItems.displayClone(source);
        ItemMeta copy = clone.getItemMeta();

        assertEquals(java.util.Set.of(ItemFlag.HIDE_ATTRIBUTES), copy.getItemFlags());
        assertEquals(java.util.Map.of(), clone.getEnchantments());
        assertTrue(copy.getPersistentDataContainer().getKeys().isEmpty());
        assertFalse(copy.getItemFlags().contains(ItemFlag.HIDE_ENCHANTS));
        assertEquals("Plain", copy.getDisplayName());
        assertFalse(copy.hasLore());
    }

    private static ItemStack named(String name, String... lore) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }
}
