package org.pvpcore.features.kits;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.pvpcore.config.kits.ItemFilterRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The browser ceiling must be the same effective enchant policy Apply enforces. */
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
    void explicitPositiveCapIsTheEffectiveCeiling() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-filter.enabled", true);
        config.set("item-filter.enchantments.max-levels.sharpness", 2);
        ItemFilter filter = new ItemFilter(ItemFilterRules.parse(config), null);

        assertEquals(2, filter.effectiveMaxLevel(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS));
        assertTrue(filter.enabled());
    }

    @Test
    void absentAndExplicitZeroCapsFallBackToVanillaMax() {
        YamlConfiguration explicitZero = new YamlConfiguration();
        explicitZero.set("item-filter.enabled", true);
        explicitZero.set("item-filter.enchantments.max-levels.sharpness", 0);

        ItemFilter absent = new ItemFilter(ItemFilterRules.parse(new YamlConfiguration()), null);
        ItemFilter zero = new ItemFilter(ItemFilterRules.parse(explicitZero), null);

        assertEquals(Enchantment.SHARPNESS.getMaxLevel(),
                absent.effectiveMaxLevel(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS));
        assertEquals(Enchantment.SHARPNESS.getMaxLevel(),
                zero.effectiveMaxLevel(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS));
        assertEquals(0, absent.maxLevel(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS));
        assertEquals(0, zero.maxLevel(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS));
    }

    @Test
    void bookCapComesFromTheEnchantedBookOverride() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-filter.enabled", true);
        config.set("item-filter.overrides.ENCHANTED_BOOK.enchantments.max-levels.sharpness", 4);
        ItemFilter filter = new ItemFilter(ItemFilterRules.parse(config), null);

        assertEquals(4, filter.effectiveMaxLevel(new ItemStack(Material.ENCHANTED_BOOK), Enchantment.SHARPNESS));
        assertEquals(Enchantment.SHARPNESS.getMaxLevel(),
                filter.effectiveMaxLevel(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS));
    }

    @Test
    void disabledPolicyDoesNotEnforceIncompatible() {
        ItemFilter filter = new ItemFilter(ItemFilterRules.parse(null), null);

        assertFalse(filter.enabled());
        assertTrue(filter.allowsIncompatible(new ItemStack(Material.DIAMOND_SWORD)));
    }

    @Test
    void enabledPolicyUsesTheMaterialRuleForIncompatible() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-filter.enabled", true);
        config.set("item-filter.enchantments.allow-incompatible", true);
        ItemFilter filter = new ItemFilter(ItemFilterRules.parse(config), null);

        assertTrue(filter.allowsIncompatible(new ItemStack(Material.DIAMOND_SWORD)));
    }
}
