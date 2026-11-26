package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.cosmetics.BannerDesign;
import org.pvpcore.features.kits.cosmetics.CosmeticApplier;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;
import java.util.Locale;

/**
 * Dedicated full-canvas shield banner designer. Stages a {@link BannerDesign} onto the
 * session's shield target; Apply here only stages (the main Customize screen commits).
 */
public final class BannerDesignerPage extends GuiScreen {
    private final GUI gui;
    private final CustomizeSession session;
    private final int kitSlot;
    private BannerDesign design;
    private final BannerDesign original;
    private int selectedLayer = -1;

    private BannerDesignerPage(GUI gui, Player player, CustomizeSession session, int kitSlot,
                               BannerDesign initial, int selectedLayer) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, org.pvpcore.gui.base.GuiFrame.Density.NONE,
                "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.kitSlot = kitSlot;
        ItemStack target = session.target();
        BannerDesign fromShield = BannerDesign.from(
                target == null ? new ItemStack(Material.SHIELD) : target);
        this.original = fromShield;
        this.design = initial == null ? fromShield : initial;
        this.selectedLayer = resumeLayerIndex(this.design, selectedLayer);
        gui.registerCustomizeScreen(player.getUniqueId());
    }

    /** Fresh designer: start from the shield's current design. */
    public static void open(GUI gui, Player player, CustomizeSession session, int kitSlot) {
        if (rejectIfStale(gui, player)) return;
        open(gui, player, session, kitSlot, null, -1);
    }

    /** Reopen with the live design (pattern-picker return); {@code design} is never null here. */
    public static void open(GUI gui, Player player, CustomizeSession session, int kitSlot,
                            BannerDesign design) {
        if (rejectIfStale(gui, player)) return;
        open(gui, player, session, kitSlot, design, -1);
    }

    /** Reopen with the live design, keeping the given layer selected. */
    public static void open(GUI gui, Player player, CustomizeSession session, int kitSlot,
                            BannerDesign design, int selectedLayer) {
        if (rejectIfStale(gui, player)) return;
        if (session == null || player == null || gui == null || gui.kits() == null) return;
        if (!org.pvpcore.commands.kits.core.ActionGuards.allowed(
                player, "perplayerkit.kit", LocationFeature.KITS)) return;
        if (!rulesOf(gui).enabled()) {
            gui.kits().lang().send(player, "error.cosmetics-disabled");
            return;
        }
        ItemStack target = session.target();
        if (target == null || target.getType() != Material.SHIELD) {
            gui.kits().lang().send(player, "error.cosmetics-not-shield");
            return;
        }
        if (new CosmeticValidator(rulesOf(gui)).hasUnreadableBannerLayers(target)) {
            gui.kits().lang().send(player, "error.cosmetics-banner-locked");
            return;
        }
        new BannerDesignerPage(gui, player, session, kitSlot, design, selectedLayer).show();
    }

    /** Whether a cursor item may be adopted as a banner design template. */
    static boolean isImportableTemplate(ItemStack cursor) {
        return cursor != null && !cursor.getType().isAir()
                && cursor.getItemMeta() instanceof BannerMeta;
    }

    /** Clamps a resumed layer index into the design's range; -1 means no selection. */
    static int resumeLayerIndex(BannerDesign design, int requested) {
        if (design == null || requested < 0 || requested >= design.size()) return -1;
        return requested;
    }

    private static CustomizeRules rulesOf(GUI gui) {
        return CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize"));
    }

    /** Refuses a pre-reload screen before it can construct a new designer/picker screen. */
    private static boolean rejectIfStale(GUI gui, Player player) {
        if (gui == null || player == null || !gui.isCustomizeStale(player.getUniqueId())) return false;
        if (gui.kits() != null) gui.kits().lang().send(player, "error.cosmetics-kit-changed");
        return true;
    }

    @Override
    protected String title() {
        if (kits() == null) return "Banner Designer";
        return GuiMenuFactory.sectionTitle(kits(), GuiTheme.ACCENT + "<bold>Banner Designer");
    }

    @Override
    protected void renderContent(StaticPane content) {
        List<BannerDesign.Layer> layers = design.layers();
        for (int i = 0; i < layers.size() && i <= GuiLayouts.BANNER_LAYERS_LAST - GuiLayouts.BANNER_LAYERS_FIRST; i++) {
            BannerDesign.Layer layer = layers.get(i);
            ItemStack icon = bannerIcon(layer.dye(),
                    GuiTheme.dim("Layer " + (i + 1)) + " " + GuiTheme.hint("Click", "edit"));
            if (i == selectedLayer) icon = GuiTheme.glow(icon);
            final int index = i;
            content.addItem(wrap(icon, (p, e) -> {
                SoundManager.playClick(p);
                selectedLayer = index;
                // Editing returns through the picker's index-bearing open(..., design, layer) overload.
                BannerPatternPickerPage.open(gui, p, design, index, session, kitSlot);
            }, player), (GuiLayouts.BANNER_LAYERS_FIRST + i) % GuiLayouts.COLUMNS,
                    (GuiLayouts.BANNER_LAYERS_FIRST + i) / GuiLayouts.COLUMNS);
        }
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // Template slot (virtual ghost) + preview.
        chrome.addItem(new GuiItem(new ItemStack(Material.WHITE_BANNER)),
                GuiLayouts.BANNER_TEMPLATE_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_TEMPLATE_SLOT / GuiLayouts.COLUMNS);
        ItemStack preview = new ItemStack(Material.SHIELD);
        try {
            preview = design.applyTo(preview);
        } catch (RuntimeException ignored) {
            // Preview is cosmetic; a bad design must never break the screen.
        }
        chrome.addItem(new GuiItem(preview), GuiLayouts.BANNER_PREVIEW_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_PREVIEW_SLOT / GuiLayouts.COLUMNS);

        // Base colour: an explicit click cycles to the next dye, then back to unset.
        chrome.addItem(wrap(bannerIcon(design.baseColor() == null ? DyeColor.WHITE : design.baseColor(),
                GuiTheme.dim("Base colour"), GuiTheme.hint("Click", "cycle")), (p, e) -> {
            SoundManager.playClick(p);
            design = design.withBaseColor(nextBase(design.baseColor()));
            refresh();
        }, player), GuiLayouts.BANNER_BASE_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_BASE_SLOT / GuiLayouts.COLUMNS);

        // Add a layer.
        chrome.addItem(wrap(GuiTheme.named(Material.SLIME_BALL, GuiTheme.GREEN + "<bold>", "Add layer",
                GuiTheme.hint("Click", "choose pattern and dye")), (p, e) -> {
            SoundManager.playClick(p);
            BannerPatternPickerPage.open(gui, p, design, -1, session, kitSlot);
        }, player), GuiLayouts.BANNER_ADD_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_ADD_SLOT / GuiLayouts.COLUMNS);

        // Remove the selected layer.
        chrome.addItem(wrap(GuiTheme.named(Material.BARRIER, GuiTheme.MUTED + "<bold>", "Remove layer",
                GuiTheme.dim("Selected layer"), GuiTheme.hint("Click", "remove")), (p, e) -> {
            SoundManager.playClick(p);
            if (selectedLayer < 0 || selectedLayer >= design.size()) return;
            design = design.removeLayer(selectedLayer);
            selectedLayer = -1;
            refresh();
        }, player), GuiLayouts.BANNER_REMOVE_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_REMOVE_SLOT / GuiLayouts.COLUMNS);

        // Reorder the selected layer.
        chrome.addItem(wrap(GuiTheme.named(Material.ARROW, GuiTheme.AQUA + "<bold>", "Move up",
                GuiTheme.hint("Click", "earlier layer")), (p, e) -> {
            SoundManager.playClick(p);
            if (selectedLayer <= 0 || selectedLayer >= design.size()) return;
            design = design.moveLayer(selectedLayer, selectedLayer - 1);
            selectedLayer--;
            refresh();
        }, player), GuiLayouts.BANNER_UP_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_UP_SLOT / GuiLayouts.COLUMNS);
        chrome.addItem(wrap(GuiTheme.named(Material.ARROW, GuiTheme.AQUA + "<bold>", "Move down",
                GuiTheme.hint("Click", "later layer")), (p, e) -> {
            SoundManager.playClick(p);
            if (selectedLayer < 0 || selectedLayer >= design.size() - 1) return;
            design = design.moveLayer(selectedLayer, selectedLayer + 1);
            selectedLayer++;
            refresh();
        }, player), GuiLayouts.BANNER_DOWN_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_DOWN_SLOT / GuiLayouts.COLUMNS);

        // Blank / Revert / Back.
        chrome.addItem(wrap(GuiTheme.named(Material.WHITE_BANNER, GuiTheme.MUTED + "<bold>", "Blank",
                GuiTheme.dim("Clear all layers")), (p, e) -> {
            SoundManager.playClick(p);
            design = BannerDesign.blank();
            selectedLayer = -1;
            refresh();
        }, player), GuiLayouts.BANNER_BLANK_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_BLANK_SLOT / GuiLayouts.COLUMNS);
        chrome.addItem(wrap(GuiTheme.named(Material.BARRIER, GuiTheme.MUTED + "<bold>", "Revert",
                GuiTheme.dim("Restore original")), (p, e) -> {
            SoundManager.playClick(p);
            design = original;
            selectedLayer = -1;
            refresh();
        }, player), GuiLayouts.BANNER_REVERT_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_REVERT_SLOT / GuiLayouts.COLUMNS);
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            CustomizePage.reopen(gui, p, session, session.targetSlot(), kitSlot);
        }, player), GuiLayouts.BANNER_BACK_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_BACK_SLOT / GuiLayouts.COLUMNS);

        // Apply (stage only, never save).
        chrome.addItem(wrap(GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Apply",
                GuiTheme.dim("Stage this design")), (p, e) -> {
            SoundManager.playClick(p);
            CustomizeRules rules = rulesOf(gui);
            if (!rules.enabled()) {
                gui.kits().lang().send(p, "error.cosmetics-disabled");
                return;
            }
            CosmeticValidator validator = new CosmeticValidator(rules);
            ItemStack target = session.target();
            if (!validator.isShield(target) || validator.hasUnreadableBannerLayers(target)) {
                gui.kits().lang().send(p, "error.cosmetics-banner-locked");
                return;
            }
            try {
                session.stageTarget(new CosmeticApplier(rules, validator).applyBanner(target, design));
            } catch (IllegalArgumentException ex) {
                // Unknown/unavailable pattern: fail closed, leaving the staged shield untouched.
                gui.kits().lang().send(p, "error.cosmetics-banner-locked");
                return;
            }
            CustomizePage.reopen(gui, p, session, session.targetSlot(), kitSlot);
        }, player), GuiLayouts.BANNER_APPLY_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_APPLY_SLOT / GuiLayouts.COLUMNS);
    }

    private static DyeColor nextBase(DyeColor current) {
        DyeColor[] colors = DyeColor.values();
        if (current == null) return colors.length == 0 ? null : colors[0];
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == current) {
                return i + 1 < colors.length ? colors[i + 1] : null;
            }
        }
        return null;
    }

    private static ItemStack bannerIcon(DyeColor dye, String... lore) {
        Material material = Material.matchMaterial(dye.name().toLowerCase(Locale.ROOT) + "_banner");
        return GuiTheme.named(material == null ? Material.WHITE_BANNER : material,
                GuiTheme.TEXT + "<bold>", dye.name(), lore);
    }

    @Override
    protected void protect(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        chest.setOnTopClick(event -> {
            // configure() runs after protect(): re-assert readOnly's blanket top-click cancel
            // before any slot-specific behaviour, so no decorative control is vanilla-pickable.
            event.setCancelled(true);
            if (event.getRawSlot() != GuiLayouts.BANNER_TEMPLATE_SLOT) return;
            ItemStack cursor = event.getCursor();
            if (!isImportableTemplate(cursor)) return;
            try {
                design = BannerDesign.from(cursor);
                selectedLayer = -1;
                refresh();
            } catch (RuntimeException ignored) {
                // A non-banner or unreadable template must not change the design.
            }
        });
        chest.setOnClose(event -> {
            if (event.getPlayer() instanceof Player closer) {
                gui.unregisterCustomizeScreen(closer.getUniqueId());
            }
        });
    }
}
