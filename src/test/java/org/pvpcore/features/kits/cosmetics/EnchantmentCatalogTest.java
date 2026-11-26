package org.pvpcore.features.kits.cosmetics;

import io.papermc.paper.enchantments.EnchantmentRarity;
import io.papermc.paper.registry.set.RegistryKeySet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stable, injectable enchantment catalog with book icons and readable labels. */
class EnchantmentCatalogTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void catalogUsesDisplayNamesAndIcons() {
        var entries = EnchantmentCatalog.entries();
        assertFalse(entries.isEmpty());
        entries.forEach(e -> {
            assertNotNull(e.enchantment());
            assertNotNull(e.label());
            assertFalse(e.icon().getType().isAir());
        });
    }

    @Test
    void entriesAreSortedByDisplayName() {
        List<String> labels = EnchantmentCatalog.entries().stream()
                .map(e -> plain(e.label()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        List<String> actual = EnchantmentCatalog.entries().stream()
                .map(e -> plain(e.label()))
                .toList();
        assertEquals(labels, actual);
    }

    @Test
    void entryCarriesStableKeyDescriptionCategoryAndVanillaMax() {
        var entry = EnchantmentCatalog.entries().getFirst();
        assertNotNull(entry.key());
        assertTrue(entry.key().contains(":"), "the key is namespaced and independent of display text");
        assertNotNull(entry.description());
        assertFalse(plain(entry.description()).isBlank());
        assertFalse(entry.category().isBlank());
        assertTrue(entry.vanillaMax() >= 1);
    }

    @Test
    void bookIconCarriesStoredEnchantAtSelectedLevelWithoutHidingIt() {
        var entry = EnchantmentCatalog.entries().stream()
                .filter(e -> e.vanillaMax() >= 2)
                .findFirst()
                .orElseThrow();

        ItemStack icon = entry.icon(2);
        assertEquals(org.bukkit.Material.ENCHANTED_BOOK, icon.getType());
        ItemMeta meta = icon.getItemMeta();
        assertTrue(meta instanceof EnchantmentStorageMeta, "book icons use stored enchant meta");
        EnchantmentStorageMeta book = (EnchantmentStorageMeta) meta;
        assertEquals(2, book.getStoredEnchants().getOrDefault(entry.enchantment(), 0));
        assertFalse(book.getItemFlags().contains(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS));
        assertEquals(0, ((EnchantmentStorageMeta) entry.icon(0).getItemMeta())
                .getStoredEnchants().getOrDefault(entry.enchantment(), 0));
    }

    @Test
    void injectedRegistryIsUsedAndEmptyCatalogIsSafe() {
        assertTrue(new EnchantmentCatalog(List.of()).getEntries().isEmpty());
        var injected = EnchantmentCatalog.entries().subList(0, 2);
        var entries = new EnchantmentCatalog(injected.stream().map(e -> e.enchantment()).toList()).getEntries();
        assertEquals(2, entries.size());
        assertEquals(injected.stream().map(e -> e.key()).sorted().toList(),
                entries.stream().map(e -> e.key()).sorted().toList());
    }

    @Test
    void identicalHumanLabelsTieBreakByKey() {
        Enchantment later = stub("z-mod:same", Component.text("Same"), null);
        Enchantment earlier = stub("a-mod:same", Component.text("Same"), null);
        var entries = new EnchantmentCatalog(List.of(later, earlier)).getEntries();

        assertEquals(List.of("a-mod:same", "z-mod:same"), entries.stream().map(e -> e.key()).toList());
        assertEquals(List.of("Same", "Same"), entries.stream().map(e -> plain(e.label())).toList());
    }

    @Test
    void translationKeyDisplayNameFallsBackToHumanReadableKey() {
        Enchantment stub = stub("test-mod:wrap_around", Component.translatable("enchantment.test.wrap"), null);
        var entry = new EnchantmentCatalog(List.of(stub)).getEntries().getFirst();
        assertEquals("Wrap Around", plain(entry.label()));
        assertEquals("test-mod:wrap_around", entry.key());
    }

    @Test
    void missingDescriptionAndPrimaryItemsFallBackSafely() {
        Enchantment stub = stub("test-mod:wrap_around", Component.text("Wrap Around"), null);
        var entry = new EnchantmentCatalog(List.of(stub)).getEntries().getFirst();
        assertFalse(plain(entry.description()).isBlank());
        assertEquals("Test Mod", entry.category());
    }

    @Test
    void malformedEntriesAreSkippedWithoutKillingTheCatalog() {
        Enchantment broken = stub("test-mod:broken", Component.text("Broken"), null, true);
        Enchantment good = stub("test-mod:good", Component.text("Good"), null, false);
        var entries = new EnchantmentCatalog(java.util.Arrays.asList(null, broken, good)).getEntries();
        assertEquals(1, entries.size());
        assertEquals("test-mod:good", entries.getFirst().key());
        assertEquals("Good", plain(entries.getFirst().label()));
    }

    private static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Minimal enchantment stub: no supported-item lookups, which MockBukkit does not emulate. */
    private static Enchantment stub(String key, Component display, Component description) {
        return stub(key, display, description, false);
    }

    private static Enchantment stub(String key, Component display, Component description, boolean broken) {
        return new Enchantment() {
            @Override public NamespacedKey getKey() { return NamespacedKey.fromString(key); }
            @Override public String getName() { return key; }
            @Override public int getMaxLevel() { return 2; }
            @Override public int getStartLevel() { return 1; }
            @Override public EnchantmentTarget getItemTarget() { return EnchantmentTarget.ALL; }
            @Override public boolean isTreasure() { return false; }
            @Override public boolean isCursed() { return false; }
            @Override public boolean conflictsWith(Enchantment other) { return false; }
            @Override public boolean canEnchantItem(ItemStack item) { return false; }
            @Override public Component displayName(int level) {
                if (broken) throw new IllegalStateException("broken registry entry");
                return display;
            }
            @Override public boolean isTradeable() { return false; }
            @Override public boolean isDiscoverable() { return false; }
            @Override public int getMinModifiedCost(int level) { return 0; }
            @Override public int getMaxModifiedCost(int level) { return 0; }
            @Override public int getAnvilCost() { return 0; }
            @Override public EnchantmentRarity getRarity() { return null; }
            @Override public float getDamageIncrease(int level, EntityCategory category) { return 0; }
            @Override public float getDamageIncrease(int level, EntityType type) { return 0; }
            @Override public Set<EquipmentSlotGroup> getActiveSlotGroups() { return Set.of(); }
            @Override public Component description() { return description; }
            @Override public RegistryKeySet<ItemType> getSupportedItems() { throw new UnsupportedOperationException(); }
            @Override public RegistryKeySet<ItemType> getPrimaryItems() { throw new UnsupportedOperationException(); }
            @Override public int getWeight() { return 0; }
            @Override public RegistryKeySet<Enchantment> getExclusiveWith() { return null; }
            @Override public String getTranslationKey() { return key; }
            @Override public String translationKey() { return key; }
        };
    }
}
