package org.pvpcore.gui.rtp;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.features.teleport.rtp.RtpModule;
import org.pvpcore.features.teleport.rtp.RtpRegion;

import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for the RTP pages. Everything here is a pure static function or
 * a constant; the pages hold the module references and ask this class for the
 * small pieces of logic they share.
 */
final class RtpSupport {

    /** Materials selectable as a destination icon. */
    static final List<Material> ICONS = List.of(
            Material.GRASS_BLOCK, Material.SHORT_GRASS, Material.DIRT, Material.STONE,
            Material.SAND, Material.RED_SAND, Material.NETHERRACK, Material.END_STONE,
            Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM, Material.SNOW_BLOCK, Material.ICE,
            Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.CACTUS, Material.SUGAR_CANE,
            Material.WATER_BUCKET, Material.LAVA_BUCKET, Material.COMPASS, Material.RECOVERY_COMPASS,
            Material.NETHER_STAR, Material.END_CRYSTAL, Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
            Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT,
            Material.CHEST, Material.ENDER_CHEST, Material.BEACON, Material.TARGET,
            Material.FIREWORK_ROCKET, Material.ELYTRA, Material.SHIELD, Material.TOTEM_OF_UNDYING
    );

    /** Entries per page of a 3-row grid. Screens shrink to fit fewer entries. */
    static final int GRID_PER_PAGE = 3 * GuiLayouts.COLUMNS;

    private RtpSupport() {
    }

    /** Content rows (1-based count) a grid screen needs for the requested page. */
    static int gridRows(int total, int page, int perPage) {
        int pages = pageCount(total, perPage);
        int shown = Math.min(perPage, Math.max(0, total - clampPage(page, pages) * perPage));
        return Math.max(1, (shown + GuiLayouts.COLUMNS - 1) / GuiLayouts.COLUMNS);
    }

    /** Lays items out in rows of nine under the top frame, each row centered. */
    static void placeGrid(StaticPane pane, java.util.List<GuiItem> items) {
        for (int row = 0; row * GuiLayouts.COLUMNS < items.size(); row++) {
            int from = row * GuiLayouts.COLUMNS;
            int to = Math.min(items.size(), from + GuiLayouts.COLUMNS);
            GuiLayouts.centerItems(pane, items.subList(from, to), GuiLayouts.contentRow(row));
        }
    }

    static int pageCount(int size, int pageSize) {
        return Math.max(1, (int) Math.ceil(size / (double) pageSize));
    }

    /** Clamps the requested page into range (0-based). */
    static int clampPage(int page, int pageCount) {
        return Math.max(0, Math.min(page, pageCount - 1));
    }

    /** "FIREWORK_ROCKET" -> "Firework Rocket" */
    static String prettyMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    static RtpRegion findRegionByWorld(RtpModule rtp, String worldName) {
        return rtp.settings().regions().values().stream()
                .filter(region -> region.worldName().equalsIgnoreCase(worldName))
                .findFirst().orElse(null);
    }

    static String makeRegionId(RtpModule rtp, String worldName) {
        String base = worldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (base.isBlank()) {
            base = "rtp_world";
        }
        String candidate = base;
        int suffix = 2;
        while (rtp.settings().region(candidate) != null) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }
}
