package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.cosmetics.BannerDesign;
import org.pvpcore.features.kits.cosmetics.CosmeticCatalog;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiPager;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pattern/dye picker for one banner layer. Returns a {@link BannerDesign.Layer} to the designer.
 */
public final class BannerPatternPickerPage extends GuiScreen {
    // Dyes occupy rows 0-1 (slots 0-15); patterns start at row 2 to avoid overlap.
    private static final int PER_PAGE = GuiLayouts.COLUMNS * 3;

    private final GUI gui;
    private final CustomizeSession session;
    private final int kitSlot;
    private final BannerDesign design;
    private final int layerIndex;
    private DyeColor selectedDye;
    private int page;

    private BannerPatternPickerPage(GUI gui, Player player, BannerDesign design, int layerIndex,
                                    CustomizeSession session, int kitSlot) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, org.pvpcore.gui.base.GuiFrame.Density.NONE,
                "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.design = design;
        this.layerIndex = layerIndex;
        this.session = session;
        this.kitSlot = kitSlot;
        List<BannerDesign.Layer> layers = design.layers();
        this.selectedDye = layerIndex >= 0 && layerIndex < layers.size()
                ? layers.get(layerIndex).dye() : DyeColor.WHITE;
        gui.registerCustomizeScreen(player.getUniqueId());
    }

    public static void open(GUI gui, Player player, BannerDesign design, int layerIndex,
                            CustomizeSession session, int kitSlot) {
        if (design == null || session == null || player == null || gui == null) return;
        if (!org.pvpcore.commands.kits.core.ActionGuards.allowed(
                player, "perplayerkit.kit", LocationFeature.KITS)) return;
        ItemStack target = session.target();
        CustomizeRules rules = CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize"));
        if (!rules.enabled()) {
            gui.kits().lang().send(player, "error.cosmetics-disabled");
            return;
        }
        if (!new CosmeticValidator(rules).isShield(target)
                || new CosmeticValidator(rules).hasUnreadableBannerLayers(target)) {
            gui.kits().lang().send(player, "error.cosmetics-banner-locked");
            return;
        }
        new BannerPatternPickerPage(gui, player, design, layerIndex, session, kitSlot).show();
    }

    @Override
    protected String title() {
        return GuiTheme.ACCENT + "<bold>Pick Pattern";
    }

    @Override
    protected void renderContent(StaticPane content) {
        // Dye swatches: selecting one sets the dye for the next pattern click.
        DyeColor[] dyes = DyeColor.values();
        for (int i = 0; i < dyes.length && i < GuiLayouts.COLUMNS * 2; i++) {
            final DyeColor dye = dyes[i];
            ItemStack icon = dyeIcon(dye, GuiTheme.hint("Click", "select dye"));
            if (dye == selectedDye) icon = GuiTheme.glow(icon);
            content.addItem(wrap(icon, (p, e) -> {
                SoundManager.playClick(p);
                selectedDye = dye;
                refresh();
            }, player), i % GuiLayouts.COLUMNS, i / GuiLayouts.COLUMNS);
        }

        CustomizeRules rules = CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize"));
        List<CosmeticCatalog.Entry> entries = CosmeticCatalog.fromRegistries(rules).bannerPatterns();
        GuiPager pager = GuiPager.of(page, entries.size(), PER_PAGE);
        page = pager.currentPage();
        int slot = GuiLayouts.COLUMNS * 2; // 18: first free slot after the dye swatches
        for (CosmeticCatalog.Entry entry : pager.slice(entries)) {
            final String key = entry.key();
            content.addItem(wrap(GuiTheme.named(entry.icon().getType(), GuiTheme.TEXT + "<bold>",
                    entry.label(), GuiTheme.dim(key), GuiTheme.hint("Click", "add layer")), (p, e) -> {
                SoundManager.playClick(p);
                org.bukkit.block.banner.PatternType pattern = patternOf(key);
                if (pattern == null) {
                    gui.kits().lang().send(p, "error.cosmetics-banner-locked");
                    return;
                }
                int max = Math.min(CustomizeRules.HARD_MAX_LAYERS, Math.max(1, rules.maxLayers()));
                BannerDesign updated = withLayer(design, layerIndex,
                        new BannerDesign.Layer(selectedDye, pattern), max);
                if (updated == null) return; // configured/hard cap reached: no write
                BannerDesignerPage.open(gui, p, session, kitSlot, updated);
            }, player), slot % GuiLayouts.COLUMNS, slot / GuiLayouts.COLUMNS);
            slot++;
        }

        if (pager.maxPages() > 1) {
            int next = Math.min(page + 1, pager.maxPages() - 1);
            int prev = Math.max(0, page - 1);
            addPager(content, 51, GuiTheme.arrow(false, prev, pager.maxPages()), prev);
            addPager(content, 52, GuiTheme.arrow(true, next, pager.maxPages()), next);
        }
    }

    private void addPager(StaticPane pane, int slot, ItemStack icon, int target) {
        pane.addItem(wrap(icon, (p, e) -> {
            SoundManager.playClick(p);
            page = target;
            refresh();
        }, player), slot % GuiLayouts.COLUMNS, slot / GuiLayouts.COLUMNS);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // Back returns to the designer with the in-progress design intact.
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            BannerDesignerPage.open(gui, p, session, kitSlot, design);
        }, player), GuiLayouts.BANNER_BACK_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_BACK_SLOT / GuiLayouts.COLUMNS);
    }

    /** Inserts or replaces one layer using only BannerDesign's immutable operations. */
    private static BannerDesign withLayer(BannerDesign base, int index, BannerDesign.Layer layer, int maxLayers) {
        List<BannerDesign.Layer> next = new ArrayList<>(base.layers());
        if (index < 0 || index >= next.size()) {
            if (next.size() >= maxLayers) return null;
            next.add(layer);
        } else {
            next.set(index, layer);
        }
        BannerDesign result = BannerDesign.blank().withBaseColor(base.baseColor());
        for (BannerDesign.Layer existing : next) {
            result = result.addLayer(existing, maxLayers);
        }
        return result;
    }

    private static org.bukkit.block.banner.PatternType patternOf(String key) {
        try {
            RegistryAccess access = RegistryAccess.registryAccess();
            if (access == null) return null;
            return access.getRegistry(RegistryKey.BANNER_PATTERN).get(NamespacedKey.fromString(key));
        } catch (RuntimeException e) {
            return null; // unknown/unavailable layer: fail closed, never substitute a pattern
        }
    }

    private static ItemStack dyeIcon(DyeColor dye, String... lore) {
        Material material = Material.matchMaterial(dye.name().toLowerCase(Locale.ROOT) + "_wool");
        return GuiTheme.named(material == null ? Material.WHITE_WOOL : material,
                GuiTheme.TEXT + "<bold>", dye.name(), lore);
    }

    @Override
    protected void protect(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        chest.setOnClose(event -> {
            if (event.getPlayer() instanceof Player closer) {
                gui.unregisterCustomizeScreen(closer.getUniqueId());
            }
        });
    }
}
