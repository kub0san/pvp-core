package org.pvpcore.features.kits.cosmetics;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ShieldMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerDesignTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void capAndReorder() {
        BannerDesign design = BannerDesign.from(new ItemStack(Material.SHIELD));
        for (int i = 0; i < 10; i++) design = design.addLayer(
                new BannerDesign.Layer(DyeColor.WHITE, PatternType.BORDER), 6);
        assertEquals(6, design.size());
        assertNull(design.baseColor()); // untouched null is preserved
        assertEquals(PatternType.BORDER, design.moveLayer(0, 5).layers().get(5).pattern());
    }

    @Test
    void blankHasNoLayersAndNullBase() {
        BannerDesign blank = BannerDesign.blank();
        assertEquals(0, blank.size());
        assertNull(blank.baseColor());
        assertTrue(blank.layers().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> blank.layers().add(
                new BannerDesign.Layer(DyeColor.RED, PatternType.SKULL)));
    }

    @Test
    void editsAreImmutable() {
        BannerDesign original = BannerDesign.blank();
        BannerDesign withBase = original.withBaseColor(DyeColor.RED);
        assertNull(original.baseColor());
        assertEquals(DyeColor.RED, withBase.baseColor());

        BannerDesign withLayer = withBase.addLayer(
                new BannerDesign.Layer(DyeColor.BLUE, PatternType.SKULL), 6);
        assertEquals(1, withLayer.size());
        assertEquals(0, withBase.size());
        assertEquals(0, withLayer.removeLayer(0).size());
        assertEquals(1, withLayer.size());
    }

    @Test
    void applyToPreservesNullBaseOnNoOp() {
        ItemStack shield = new ItemStack(Material.SHIELD);
        BannerDesign design = BannerDesign.from(shield);

        ItemStack applied = design.applyTo(shield);

        assertNotSame(shield, applied);
        ShieldMeta meta = (ShieldMeta) applied.getItemMeta();
        assertNull(meta.getBaseColor());
        assertTrue(meta.getPatterns().isEmpty());
    }

    @Test
    void applyToWritesBaseAndLayers() {
        ItemStack shield = new ItemStack(Material.SHIELD);
        BannerDesign design = BannerDesign.from(shield)
                .withBaseColor(DyeColor.RED)
                .addLayer(new BannerDesign.Layer(DyeColor.BLUE, PatternType.SKULL), 6);

        ItemStack applied = design.applyTo(shield);
        ShieldMeta meta = (ShieldMeta) applied.getItemMeta();
        assertEquals(DyeColor.RED, meta.getBaseColor());
        assertEquals(1, meta.getPatterns().size());
        assertEquals(DyeColor.BLUE, meta.getPatterns().get(0).getColor());
        assertEquals(PatternType.SKULL, meta.getPatterns().get(0).getPattern());
        assertNull(((ShieldMeta) shield.getItemMeta()).getBaseColor());
    }

    @Test
    void applyToRejectsNonShield() {
        assertThrows(IllegalArgumentException.class,
                () -> BannerDesign.blank().applyTo(new ItemStack(Material.DIAMOND_CHESTPLATE)));
    }
}
