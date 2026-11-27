package org.pvpcore.features.kits;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.pvpcore.config.kits.ItemFilterRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parity between the offered browser ceiling and what Apply actually enforces. */
class ItemFilterEnchantPolicyTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void absentCapWithoutOverLevelledStopsAtVanillaMax() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-filter.enabled", true);
        ItemFilter filter = new ItemFilter(ItemFilterRules.parse(config), null);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        int vanilla = Enchantment.SHARPNESS.getMaxLevel();

        assertEquals(vanilla, filter.effectiveMaxLevel(sword, Enchantment.SHARPNESS));
        assertTrue(filter.allowed(enchanted(sword, vanilla)));
        assertFalse(filter.allowed(enchanted(sword, vanilla + 1)));
    }

    @Test
    void absentCapWithOverLevelledIsUnbounded() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-filter.enabled", true);
        config.set("item-filter.enchantments.allow-over-levelled", true);
        ItemFilter filter = new ItemFilter(ItemFilterRules.parse(config), null);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        int beyond = Enchantment.SHARPNESS.getMaxLevel() + 3;

        assertEquals(ItemFilter.UNBOUNDED_ENCHANT_LEVEL, filter.effectiveMaxLevel(sword, Enchantment.SHARPNESS));
        assertTrue(filter.allowed(enchanted(sword, beyond)));
        assertTrue(filter.allowed(enchanted(sword, beyond + 1)));

        ItemStack conflicting = enchanted(sword, beyond);
        ItemMeta meta = conflicting.getItemMeta();
        meta.addEnchant(Enchantment.SMITE, 1, true);
        conflicting.setItemMeta(meta);
        assertFalse(filter.allowed(conflicting), "over-levelled does not waive the conflict rule");
    }

    @Test
    void explicitPositiveCapIsTheEffectiveCeiling() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-filter.enabled", true);
        config.set("item-filter.enchantments.max-levels.sharpness", 2);
        ItemFilter filter = new ItemFilter(ItemFilterRules.parse(config), null);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);

        assertEquals(2, filter.effectiveMaxLevel(sword, Enchantment.SHARPNESS));
        assertTrue(filter.allowed(enchanted(sword, 2)));
        assertFalse(filter.allowed(enchanted(sword, 3)));
    }

    @Test
    void explicitZeroCapIsUnboundedEvenWithoutOverLevelled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-filter.enabled", true);
        config.set("item-filter.enchantments.max-levels.sharpness", 0);
        ItemFilter filter = new ItemFilter(ItemFilterRules.parse(config), null);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        int beyond = Enchantment.SHARPNESS.getMaxLevel() + 3;

        assertEquals(ItemFilter.UNBOUNDED_ENCHANT_LEVEL, filter.effectiveMaxLevel(sword, Enchantment.SHARPNESS));
        assertTrue(filter.allowed(enchanted(sword, beyond)));
        assertTrue(filter.allowed(enchanted(sword, beyond + 1)));
    }

    @Test
    void disabledFilterIgnoresStaleCapsAndAllowsOverLevelled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-filter.enabled", false);
        config.set("item-filter.enchantments.max-levels.sharpness", 1);
        config.set("item-filter.enchantments.allow-over-levelled", false);
        ItemFilter filter = new ItemFilter(ItemFilterRules.parse(config), null);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        int beyond = Enchantment.SHARPNESS.getMaxLevel() + 5;

        assertFalse(filter.enabled());
        assertEquals(ItemFilter.UNBOUNDED_ENCHANT_LEVEL, filter.effectiveMaxLevel(sword, Enchantment.SHARPNESS));
        assertTrue(filter.allowed(enchanted(sword, beyond)));
    }

    @Test
    void bookStoredEnchantsShareTheEffectiveCap() {
        YamlConfiguration capped = new YamlConfiguration();
        capped.set("item-filter.enabled", true);
        capped.set("item-filter.overrides.ENCHANTED_BOOK.enchantments.max-levels.sharpness", 2);
        ItemFilter bookFilter = new ItemFilter(ItemFilterRules.parse(capped), null);
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);

        assertEquals(2, bookFilter.effectiveMaxLevel(book, Enchantment.SHARPNESS));
        assertTrue(bookFilter.allowed(bookWithStored(book, 2)));
        assertFalse(bookFilter.allowed(bookWithStored(book, 3)));
        assertEquals(Enchantment.SHARPNESS.getMaxLevel(),
                bookFilter.effectiveMaxLevel(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS));

        YamlConfiguration unbounded = new YamlConfiguration();
        unbounded.set("item-filter.enabled", true);
        unbounded.set("item-filter.overrides.ENCHANTED_BOOK.enchantments.max-levels.sharpness", 0);
        ItemFilter looseFilter = new ItemFilter(ItemFilterRules.parse(unbounded), null);
        int beyond = Enchantment.SHARPNESS.getMaxLevel() + 3;

        assertEquals(ItemFilter.UNBOUNDED_ENCHANT_LEVEL, looseFilter.effectiveMaxLevel(book, Enchantment.SHARPNESS));
        assertTrue(looseFilter.allowed(bookWithStored(book, beyond)));
    }

    private static ItemStack enchanted(ItemStack source, int level) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, level, true);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack bookWithStored(ItemStack source, int level) {
        ItemStack book = source.clone();
        ItemMeta meta = book.getItemMeta();
        ((EnchantmentStorageMeta) meta).addStoredEnchant(Enchantment.SHARPNESS, level, true);
        book.setItemMeta(meta);
        return book;
    }
}
