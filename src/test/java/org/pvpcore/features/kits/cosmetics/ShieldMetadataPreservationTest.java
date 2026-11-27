package org.pvpcore.features.kits.cosmetics;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ShieldMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.KitSaveResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Pins the invariant that cosmetic customization never rewrites a saved shield's banner layers. */
class ShieldMetadataPreservationTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack bannerShield() {
        ItemStack shield = new ItemStack(Material.SHIELD);
        ShieldMeta meta = (ShieldMeta) shield.getItemMeta();
        meta.setPatterns(List.of(new Pattern(DyeColor.RED, PatternType.SKULL)));
        shield.setItemMeta(meta);
        return shield;
    }

    @Test
    void savedShieldBannerLayersSurviveEditorSave() {
        ItemStack shield = bannerShield();
        ItemStack copy = shield.clone();

        assertEquals(((ShieldMeta) shield.getItemMeta()).getPatterns(),
                ((ShieldMeta) copy.getItemMeta()).getPatterns());
    }

    @Test
    void committingOtherItemEditsLeavesShieldBannerLayersIntact() {
        Player player = server.addPlayer();
        ItemStack shield = bannerShield();
        ItemStack[] kit = new ItemStack[41];
        kit[40] = shield;

        CustomizeSession session = new CustomizeSession(kit);
        session.selectTarget(0);
        session.stageTarget(new ItemStack(Material.DIAMOND_SWORD));

        AtomicReference<ItemStack[]> saved = new AtomicReference<>();
        CustomizeService service = new CustomizeService(
                CustomizeRules.parse(null),
                (viewer, slot, contents) -> {
                    saved.set(contents);
                    return new KitSaveResult(true, new CompletableFuture<>());
                },
                viewer -> true,
                (viewer, slot) -> kit,
                viewer -> false);

        assertEquals(ApplyResult.QUEUED, service.commit(player, 1, session));
        assertNotNull(saved.get());
        assertEquals(((ShieldMeta) shield.getItemMeta()).getPatterns(),
                ((ShieldMeta) saved.get()[40].getItemMeta()).getPatterns());
    }
}
