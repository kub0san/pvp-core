package org.pvpcore.features.kits.cosmetics;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.pvpcore.config.kits.CustomizeRules;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        var catalog = new CosmeticCatalog(List.of(entry), List.of(), CustomizeRules.parse(null));
        assertEquals(1, catalog.trimPatterns().size());
        assertEquals(Material.PAPER, catalog.trimPatterns().get(0).icon().getType());
    }

    @Test void allowlistFiltersByRegistryKey() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("allowed-trim-patterns", List.of("minecraft:coast"));
        var catalog = new CosmeticCatalog(List.of(
                new CosmeticCatalog.Entry("minecraft:coast", "Coast", new ItemStack(Material.PAPER)),
                new CosmeticCatalog.Entry("minecraft:eye", "Eye", new ItemStack(Material.PAPER))),
                List.of(), CustomizeRules.parse(section));
        assertEquals(List.of("minecraft:coast"), catalog.trimPatterns().stream().map(CosmeticCatalog.Entry::key).toList());
    }

    @Test void trimMaterialsUseTheirOwnAllowlist() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("allowed-trim-materials", List.of("minecraft:emerald"));
        var catalog = new CosmeticCatalog(
                List.of(),
                List.of(
                        new CosmeticCatalog.Entry("minecraft:emerald", "Emerald", new ItemStack(Material.PAPER)),
                        new CosmeticCatalog.Entry("minecraft:diamond", "Diamond", new ItemStack(Material.PAPER))),
                CustomizeRules.parse(section));
        assertEquals(List.of("minecraft:emerald"),
                catalog.trimMaterials().stream().map(CosmeticCatalog.Entry::key).toList());
        assertTrue(catalog.trimPatterns().isEmpty());
    }

    @Test void fromRegistriesDoesNotThrow() {
        var catalog = assertDoesNotThrow(() -> CosmeticCatalog.fromRegistries(CustomizeRules.parse(null)));
        assertNotNull(catalog.trimPatterns());
        assertNotNull(catalog.trimMaterials());
    }

    @Test void vanillaTrimMaterialMapsAllElevenVanillaIngredients() {
        assertAll(
                () -> assertSame(Material.IRON_INGOT, trim("iron"), "iron"),
                () -> assertSame(Material.GOLD_INGOT, trim("gold"), "gold"),
                () -> assertSame(Material.COPPER_INGOT, trim("copper"), "copper"),
                () -> assertSame(Material.NETHERITE_INGOT, trim("netherite"), "netherite"),
                () -> assertSame(Material.LAPIS_LAZULI, trim("lapis"), "lapis"),
                () -> assertSame(Material.AMETHYST_SHARD, trim("amethyst"), "amethyst"),
                () -> assertSame(Material.DIAMOND, trim("diamond"), "diamond"),
                () -> assertSame(Material.EMERALD, trim("emerald"), "emerald"),
                () -> assertSame(Material.QUARTZ, trim("quartz"), "quartz"),
                () -> assertSame(Material.REDSTONE, trim("redstone"), "redstone"),
                () -> assertSame(Material.RESIN_BRICK, trim("resin"), "resin"));
    }

    @Test void vanillaTemplateMaterialMapsAllEighteenVanillaPatterns() {
        assertAll(
                () -> assertSame(Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, template("bolt"), "bolt"),
                () -> assertSame(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, template("coast"), "coast"),
                () -> assertSame(Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, template("dune"), "dune"),
                () -> assertSame(Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, template("eye"), "eye"),
                () -> assertSame(Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, template("flow"), "flow"),
                () -> assertSame(Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, template("host"), "host"),
                () -> assertSame(Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, template("raiser"), "raiser"),
                () -> assertSame(Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, template("rib"), "rib"),
                () -> assertSame(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, template("sentry"), "sentry"),
                () -> assertSame(Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, template("shaper"), "shaper"),
                () -> assertSame(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, template("silence"), "silence"),
                () -> assertSame(Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, template("snout"), "snout"),
                () -> assertSame(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, template("spire"), "spire"),
                () -> assertSame(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, template("tide"), "tide"),
                () -> assertSame(Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, template("vex"), "vex"),
                () -> assertSame(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, template("ward"), "ward"),
                () -> assertSame(Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, template("wayfinder"), "wayfinder"),
                () -> assertSame(Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, template("wild"), "wild"));
    }

    @Test void unknownNamespaceAndPathFallBackToPaperWithoutThrowing() {
        NamespacedKey packMaterial = key("mypack:iron");
        NamespacedKey packPattern = key("mypack:bolt");
        NamespacedKey unknownMaterial = key("minecraft:not_a_trim_material");
        NamespacedKey unknownPattern = key("minecraft:not_a_trim_pattern");

        assertAll(
                () -> assertSame(Material.PAPER,
                        assertDoesNotThrow(() -> CosmeticCatalog.vanillaTrimMaterial(packMaterial))),
                () -> assertSame(Material.PAPER,
                        assertDoesNotThrow(() -> CosmeticCatalog.vanillaTrimMaterial(unknownMaterial))),
                () -> assertSame(Material.PAPER,
                        assertDoesNotThrow(() -> CosmeticCatalog.vanillaTemplateMaterial(packPattern))),
                () -> assertSame(Material.PAPER,
                        assertDoesNotThrow(() -> CosmeticCatalog.vanillaTemplateMaterial(unknownPattern))));
    }

    private static NamespacedKey key(String value) {
        return NamespacedKey.fromString(value);
    }

    private static Material trim(String path) {
        return CosmeticCatalog.vanillaTrimMaterial(key("minecraft:" + path));
    }

    private static Material template(String path) {
        return CosmeticCatalog.vanillaTemplateMaterial(key("minecraft:" + path));
    }
}
