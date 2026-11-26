package org.pvpcore.gui.kits.editor;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.pvpcore.features.kits.cosmetics.BannerDesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure regression seams for the designer's template guard and layer-resume contract. */
class BannerDesignerPageTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static BannerDesign designWithLayers(int count) {
        BannerDesign design = BannerDesign.blank().withBaseColor(DyeColor.RED);
        for (int i = 0; i < count; i++) {
            design = design.addLayer(
                    new BannerDesign.Layer(DyeColor.WHITE, PatternType.BORDER), 16);
        }
        return design;
    }

    @Test
    void isImportableTemplateAcceptsBanner() {
        assertTrue(BannerDesignerPage.isImportableTemplate(new ItemStack(Material.RED_BANNER)));
    }

    @Test
    void isImportableTemplateAcceptsShieldWithBannerMeta() {
        assertTrue(BannerDesignerPage.isImportableTemplate(new ItemStack(Material.SHIELD)));
    }

    @Test
    void isImportableTemplateRejectsPlainNullAndAir() {
        assertFalse(BannerDesignerPage.isImportableTemplate(
                new ItemStack(Material.DIAMOND_CHESTPLATE)));
        assertFalse(BannerDesignerPage.isImportableTemplate(null));
        assertFalse(BannerDesignerPage.isImportableTemplate(new ItemStack(Material.AIR)));
    }

    @Test
    void resumeLayerIndexKeepsValidIndex() {
        BannerDesign design = designWithLayers(2);
        assertEquals(0, BannerDesignerPage.resumeLayerIndex(design, 0));
        assertEquals(1, BannerDesignerPage.resumeLayerIndex(design, 1));
    }

    @Test
    void resumeLayerIndexClampsOutOfRangeToMinusOne() {
        BannerDesign design = designWithLayers(2);
        assertEquals(-1, BannerDesignerPage.resumeLayerIndex(design, -2));
        assertEquals(-1, BannerDesignerPage.resumeLayerIndex(design, -1));
        assertEquals(-1, BannerDesignerPage.resumeLayerIndex(design, 2));
        assertEquals(-1, BannerDesignerPage.resumeLayerIndex(design, 99));
    }

    @Test
    void resumeLayerIndexWithNullDesignIsMinusOne() {
        assertEquals(-1, BannerDesignerPage.resumeLayerIndex(null, 0));
    }
}
