package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.cosmetics.BannerDesign;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

/**
 * Dedicated full-canvas shield banner designer. Stages a {@link BannerDesign} onto the
 * session's shield target; Apply here only stages (the main Customize screen commits).
 */
public final class BannerDesignerPage extends GuiScreen {
    private final GUI gui;
    private final CustomizeSession session;
    private BannerDesign design;
    private BannerDesign original;

    private BannerDesignerPage(GUI gui, Player player, CustomizeSession session) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, org.pvpcore.gui.base.GuiFrame.Density.NONE,
                "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.design = BannerDesign.from(session.target() == null ? new ItemStack(Material.SHIELD) : session.target());
        this.original = this.design;
    }

    public static void open(GUI gui, Player player, CustomizeSession session) {
        if (session == null) return;
        if (!org.pvpcore.commands.kits.core.ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return;
        if (session.target() == null || session.target().getType() != Material.SHIELD) {
            gui.kits().lang().send(player, "error.cosmetics-not-shield");
            return;
        }
        ItemStack target = session.target();
        CosmeticValidator validator = new CosmeticValidator(CustomizeRules.parse(
                gui.kits().config().getConfigurationSection("customize")));
        if (validator.hasUnreadableBannerLayers(target)) {
            gui.kits().lang().send(player, "error.cosmetics-banner-locked");
            return;
        }
        new BannerDesignerPage(gui, player, session).show();
    }

    @Override
    protected String title() {
        return GuiTheme.ACCENT + "<bold>Banner Designer";
    }

    @Override
    protected void renderContent(StaticPane content) {
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // Template slot (ghost) + preview
        chrome.addItem(new GuiItem(new ItemStack(Material.WHITE_BANNER)), GuiLayouts.BANNER_TEMPLATE_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_TEMPLATE_SLOT / GuiLayouts.COLUMNS);
        ItemStack preview = new ItemStack(Material.SHIELD);
        try { design.applyTo(preview); } catch (RuntimeException ignored) { }
        chrome.addItem(new GuiItem(preview), GuiLayouts.BANNER_PREVIEW_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.BANNER_PREVIEW_SLOT / GuiLayouts.COLUMNS);

        // Back
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            CustomizePage.reopen(gui, p, 0, session);
        }, player), GuiLayouts.BANNER_BACK_SLOT % GuiLayouts.COLUMNS, GuiLayouts.BANNER_BACK_SLOT / GuiLayouts.COLUMNS);

        // Apply (stage)
        chrome.addItem(wrap(GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Apply",
                GuiTheme.dim("Stage this design")), (p, e) -> {
            SoundManager.playClick(p);
            org.pvpcore.features.kits.cosmetics.CosmeticApplier applier =
                    new org.pvpcore.features.kits.cosmetics.CosmeticApplier(
                            CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize")),
                            new CosmeticValidator(CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize"))));
            ItemStack staged = applier.applyBanner(session.target(), design);
            session.stageTarget(staged);
            CustomizePage.reopen(gui, p, 0, session);
        }, player), GuiLayouts.BANNER_APPLY_SLOT % GuiLayouts.COLUMNS, GuiLayouts.BANNER_APPLY_SLOT / GuiLayouts.COLUMNS);

        // Blank
        chrome.addItem(wrap(GuiTheme.named(Material.WHITE_BANNER, GuiTheme.MUTED + "<bold>", "Blank",
                GuiTheme.dim("Clear all layers")), (p, e) -> {
            SoundManager.playClick(p);
            design = BannerDesign.blank();
            refresh();
        }, player), GuiLayouts.BANNER_BLANK_SLOT % GuiLayouts.COLUMNS, GuiLayouts.BANNER_BLANK_SLOT / GuiLayouts.COLUMNS);

        // Revert
        chrome.addItem(wrap(GuiTheme.named(Material.BARRIER, GuiTheme.MUTED + "<bold>", "Revert",
                GuiTheme.dim("Restore original")), (p, e) -> {
            SoundManager.playClick(p);
            design = original;
            refresh();
        }, player), GuiLayouts.BANNER_REVERT_SLOT % GuiLayouts.COLUMNS, GuiLayouts.BANNER_REVERT_SLOT / GuiLayouts.COLUMNS);
    }

    @Override
    protected void protect(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        chest.setOnTopClick(event -> {
            if (event.getRawSlot() != GuiLayouts.BANNER_TEMPLATE_SLOT) return;
            event.setCancelled(true);
            org.bukkit.inventory.ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType().isAir()) return;
            if (!(cursor.getItemMeta() instanceof org.bukkit.inventory.meta.BannerMeta)) return;
            try { design = BannerDesign.from(cursor); refresh(); } catch (RuntimeException ignored) { }
        });
    }
}
