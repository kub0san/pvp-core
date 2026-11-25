package org.pvpcore.features.kits.cosmetics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.pvpcore.config.kits.CustomizeRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticApplierTest {
    private CosmeticApplier applier;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        CustomizeRules rules = CustomizeRules.parse(null);
        applier = new CosmeticApplier(rules, new CosmeticValidator(rules));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void applyTrimLeavesSourceUnchanged() {
        ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);

        ItemStack applied = applier.applyTrim(chestplate, TrimMaterial.DIAMOND, TrimPattern.COAST);

        assertNotSame(chestplate, applied);
        assertNull(((ArmorMeta) chestplate.getItemMeta()).getTrim());
        ArmorTrim trim = ((ArmorMeta) applied.getItemMeta()).getTrim();
        assertEquals(TrimMaterial.DIAMOND, trim.getMaterial());
        assertEquals(TrimPattern.COAST, trim.getPattern());
    }

    @Test
    void removeTrimClearsTrim() {
        ItemStack trimmed = applier.applyTrim(
                new ItemStack(Material.DIAMOND_CHESTPLATE), TrimMaterial.DIAMOND, TrimPattern.COAST);

        ItemStack removed = applier.removeTrim(trimmed);

        assertNotSame(trimmed, removed);
        assertTrue(((ArmorMeta) trimmed.getItemMeta()).hasTrim());
        assertNull(((ArmorMeta) removed.getItemMeta()).getTrim());
    }

    @Test
    void trimOnShieldIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> applier.applyTrim(new ItemStack(Material.SHIELD), TrimMaterial.DIAMOND, TrimPattern.COAST));
        assertThrows(IllegalArgumentException.class,
                () -> applier.removeTrim(new ItemStack(Material.SHIELD)));
    }

    @Test
    void bannerOnChestplateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> applier.applyBanner(new ItemStack(Material.DIAMOND_CHESTPLATE), BannerDesign.blank()));
    }

    @Test
    void noOpBannerDoesNotAddBaseColour() {
        ItemStack shield = new ItemStack(Material.SHIELD);
        BannerDesign design = BannerDesign.from(shield);

        ItemStack applied = applier.applyBanner(shield, design);

        assertNotSame(shield, applied);
        ShieldMeta meta = (ShieldMeta) applied.getItemMeta();
        assertNull(meta.getBaseColor());
        assertTrue(meta.getPatterns().isEmpty());
    }

    @Test
    void applyBannerWritesDesign() {
        ItemStack shield = new ItemStack(Material.SHIELD);
        BannerDesign design = BannerDesign.from(shield)
                .withBaseColor(DyeColor.RED)
                .addLayer(new BannerDesign.Layer(DyeColor.BLUE, PatternType.SKULL), 6);

        ItemStack applied = applier.applyBanner(shield, design);

        ShieldMeta meta = (ShieldMeta) applied.getItemMeta();
        assertEquals(DyeColor.RED, meta.getBaseColor());
        assertEquals(1, meta.getPatterns().size());
        assertNull(((ShieldMeta) shield.getItemMeta()).getBaseColor());
    }

    @Test
    void applyNameUsesParsedComponentAndLeavesSourceUnchanged() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);

        ItemStack applied = applier.applyName(item, "<red>Sword</red>");

        assertNotSame(item, applied);
        assertFalse(item.getItemMeta().hasDisplayName());
        Component name = applied.getItemMeta().displayName();
        assertEquals("Sword", PlainTextComponentSerializer.plainText().serialize(name));
        assertEquals(NamedTextColor.RED, name.color());
    }

    @Test
    void invalidNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> applier.applyName(new ItemStack(Material.DIAMOND_SWORD),
                        "<click:run_command:/spawn>bad</click>"));
    }
}
