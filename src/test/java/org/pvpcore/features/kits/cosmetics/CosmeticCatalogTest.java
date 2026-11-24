package org.pvpcore.features.kits.cosmetics;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.pvpcore.config.kits.CustomizeRules;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticCatalogTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test void emptyAllowlistAllowsAllWithFallback() {
        var entry = new CosmeticCatalog.Entry("pack:lost", "unknown", new ItemStack(Material.PAPER));
        var catalog = new CosmeticCatalog(List.of(entry), List.of(), List.of(), CustomizeRules.parse(null));
        assertEquals(1, catalog.trimPatterns().size());
        assertEquals(Material.PAPER, catalog.trimPatterns().get(0).icon().getType());
    }

    @Test void allowlistFiltersByRegistryKey() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("allowed-trim-patterns", List.of("minecraft:coast"));
        var catalog = new CosmeticCatalog(List.of(
                new CosmeticCatalog.Entry("minecraft:coast", "Coast", new ItemStack(Material.PAPER)),
                new CosmeticCatalog.Entry("minecraft:eye", "Eye", new ItemStack(Material.PAPER))),
                List.of(), List.of(), CustomizeRules.parse(section));
        assertEquals(List.of("minecraft:coast"), catalog.trimPatterns().stream().map(CosmeticCatalog.Entry::key).toList());
    }

    @Test void materialsAndBannersUseIndependentAllowlists() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("allowed-trim-materials", List.of("minecraft:emerald"));
        section.set("allowed-banner-patterns", List.of("minecraft:skull"));
        var catalog = new CosmeticCatalog(
                List.of(),
                List.of(
                        new CosmeticCatalog.Entry("minecraft:emerald", "Emerald", new ItemStack(Material.PAPER)),
                        new CosmeticCatalog.Entry("minecraft:diamond", "Diamond", new ItemStack(Material.PAPER))),
                List.of(
                        new CosmeticCatalog.Entry("minecraft:skull", "Skull", new ItemStack(Material.PAPER)),
                        new CosmeticCatalog.Entry("minecraft:border", "Border", new ItemStack(Material.PAPER))),
                CustomizeRules.parse(section));
        assertEquals(List.of("minecraft:emerald"),
                catalog.trimMaterials().stream().map(CosmeticCatalog.Entry::key).toList());
        assertEquals(List.of("minecraft:skull"),
                catalog.bannerPatterns().stream().map(CosmeticCatalog.Entry::key).toList());
        assertTrue(catalog.trimPatterns().isEmpty());
    }

    @Test void fromRegistriesDoesNotThrow() {
        var catalog = assertDoesNotThrow(() -> CosmeticCatalog.fromRegistries(CustomizeRules.parse(null)));
        assertNotNull(catalog.trimPatterns());
        assertNotNull(catalog.trimMaterials());
        assertNotNull(catalog.bannerPatterns());
    }
}
