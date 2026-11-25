package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.cosmetics.ApplyResult;
import org.pvpcore.features.kits.cosmetics.CosmeticApplier;
import org.pvpcore.features.kits.cosmetics.CosmeticCatalog;
import org.pvpcore.features.kits.cosmetics.CosmeticTarget;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.features.kits.cosmetics.CustomizeService;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiPager;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;
import java.util.UUID;

/**
 * Staged cosmetic editor for one personal kit.
 */
public final class CustomizePage extends GuiScreen {
    private enum Mode { TRIM, BANNER, RENAME }

    private final GUI gui;
    private final int kitSlot;
    private final CustomizeSession session;

    // Trim staging state. Browsing never mutates the session; both must be chosen before Apply.
    private String selectedTrimPattern;
    private String selectedTrimMaterial;
    private boolean trimMaterialStep;
    private int page;
    private Mode mode = Mode.TRIM;
    private boolean writePending;

    private CustomizePage(GUI gui, Player player, int kitSlot, CustomizeSession session) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, org.pvpcore.gui.base.GuiFrame.Density.NONE,
                "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.kitSlot = kitSlot;
        this.session = session;
        gui.registerCustomizeScreen(player.getUniqueId());
    }

    /** Fresh entry from the personal kit editor: a previous reload marker no longer applies. */
    public static void open(GUI gui, Player player, int kitSlot, CustomizeSession session) {
        if (!canOpen(gui, player, session)) return;
        gui.clearCustomizeStale(player.getUniqueId());
        new CustomizePage(gui, player, kitSlot, session).show();
    }

    /** Reopens with an existing session (rename/banner return, save failure); stale state stays. */
    public static void reopen(GUI gui, Player player, int kitSlot, CustomizeSession session) {
        if (!canOpen(gui, player, session)) return;
        new CustomizePage(gui, player, kitSlot, session).show();
    }

    private static boolean canOpen(GUI gui, Player player, CustomizeSession session) {
        if (gui == null || player == null || session == null) return false;
        if (!ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return false;
        if (gui.kits() == null) return false;
        if (!gui.kits().itemFilter().isReady()) {
            gui.kits().lang().send(player, "error.kitroom-not-ready");
            return false;
        }
        if (!CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize")).enabled()) {
            gui.kits().lang().send(player, "error.cosmetics-disabled");
            return false;
        }
        return true;
    }

    @Override
    protected String title() {
        if (kits() == null) return "Customize Kit " + kitSlot;
        // GuiScreen expects a legacy title; sectionTitle converts MiniMessage so the brand
        // colour and bold actually render instead of showing literal tags in-game.
        return GuiMenuFactory.sectionTitle(kits(), GuiTheme.ACCENT + "<bold>Customize Kit " + kitSlot);
    }

    private CustomizeRules rules() {
        return CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize"));
    }

    /** Resolves both selected registry keys; null means a key is unknown or unavailable. */
    private static TrimChoice resolveTrimChoice(String patternKey, String materialKey) {
        if (patternKey == null || materialKey == null) return null;
        try {
            RegistryAccess access = RegistryAccess.registryAccess();
            if (access == null) return null;
            NamespacedKey patternId = NamespacedKey.fromString(patternKey);
            NamespacedKey materialId = NamespacedKey.fromString(materialKey);
            if (patternId == null || materialId == null) return null;
            TrimPattern pattern = access.getRegistry(RegistryKey.TRIM_PATTERN).get(patternId);
            TrimMaterial material = access.getRegistry(RegistryKey.TRIM_MATERIAL).get(materialId);
            return pattern == null || material == null ? null : new TrimChoice(material, pattern);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static CosmeticApplier applier(CustomizeRules rules, CosmeticValidator validator) {
        return new CosmeticApplier(rules, validator);
    }

    private record TrimChoice(TrimMaterial material, TrimPattern pattern) {}

    @Override
    protected void renderContent(StaticPane content) {
        if (mode != Mode.TRIM) return;
        // Trim picker: pattern step or material step, paged.
        CosmeticCatalog catalog = CosmeticCatalog.fromRegistries(rules());
        List<CosmeticCatalog.Entry> entries = trimMaterialStep
                ? catalog.trimMaterials() : catalog.trimPatterns();
        GuiPager pager = GuiPager.of(page, entries.size(), GuiLayouts.COLUMNS * 4);
        page = pager.currentPage();
        int row = 1;
        int col = 0;
        for (CosmeticCatalog.Entry entry : pager.slice(entries)) {
            final String key = entry.key();
            // Use the catalog label fallback so data-pack entries are not shown as blank icons.
            ItemStack icon = GuiTheme.named(entry.icon().getType(), GuiTheme.TEXT + "<bold>",
                    entry.label(), GuiTheme.dim(key), GuiTheme.hint("Click", "select"));
            String selected = trimMaterialStep ? selectedTrimMaterial : selectedTrimPattern;
            if (key.equals(selected)) {
                icon = GuiTheme.glow(icon);
            }
            content.addItem(wrap(icon, (p, e) -> {
                SoundManager.playClick(p);
                if (trimMaterialStep) {
                    selectedTrimMaterial = key;
                } else {
                    selectedTrimPattern = key;
                    trimMaterialStep = true;
                }
                refresh();
            }, player), col, row);
            col++;
            if (col >= GuiLayouts.COLUMNS) { col = 0; row++; }
        }
        if (pager.maxPages() > 1) {
            int prev = Math.max(0, page - 1);
            int next = Math.min(page + 1, pager.maxPages() - 1);
            addTrimPager(content, 51, GuiTheme.arrow(false, prev, pager.maxPages()), prev);
            addTrimPager(content, 52, GuiTheme.arrow(true, next, pager.maxPages()), next);
        }
    }

    private void addTrimPager(StaticPane pane, int slot, ItemStack icon, int target) {
        pane.addItem(wrap(icon, (p, e) -> {
            SoundManager.playClick(p);
            page = target;
            refresh();
        }, player), slot % GuiLayouts.COLUMNS, slot / GuiLayouts.COLUMNS);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // Back
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            gui.clearPendingCustomize(p.getUniqueId());
            session.discard();
            gui.flushOpenEditor(p);
            ItemEditorPage.openKit(gui, p, kitSlot);
        }, player), GuiLayouts.CUSTOMIZE_BACK_SLOT % GuiLayouts.COLUMNS, GuiLayouts.CUSTOMIZE_BACK_SLOT / GuiLayouts.COLUMNS);

        // Reset
        chrome.addItem(wrap(GuiTheme.named(Material.BARRIER, GuiTheme.MUTED + "<bold>", "Reset",
                GuiTheme.dim("Restore selected target")), (p, e) -> {
            SoundManager.playClick(p);
            if (session.targetSlot() >= 0) session.resetTarget();
            clearTrimSelection();
            refresh();
        }, player), GuiLayouts.CUSTOMIZE_RESET_SLOT % GuiLayouts.COLUMNS, GuiLayouts.CUSTOMIZE_RESET_SLOT / GuiLayouts.COLUMNS);

        // Remove (Trim mode only): banner has Blank/Revert in its designer; Rename owns its clear.
        if (mode == Mode.TRIM) {
            chrome.addItem(wrap(GuiTheme.named(Material.SHEARS, GuiTheme.MUTED + "<bold>", "Remove trim",
                    GuiTheme.dim("Remove trim from the selected armor"), GuiTheme.hint("Click", "remove")), (p, e) -> {
                SoundManager.playClick(p);
                CustomizeRules rules = rules();
                CosmeticValidator validator = new CosmeticValidator(rules);
                ItemStack target = session.target();
                if (!validator.isArmor(target)) {
                    gui.kits().lang().send(p, "error.cosmetics-not-armor");
                    return;
                }
                try {
                    session.stageTarget(new CosmeticApplier(rules, validator).removeTrim(target));
                } catch (IllegalArgumentException ex) {
                    gui.kits().lang().send(p, "error.cosmetics-not-armor");
                    return;
                }
                clearTrimSelection();
                refresh();
            }, player), GuiLayouts.CUSTOMIZE_REMOVE_SLOT % GuiLayouts.COLUMNS,
                    GuiLayouts.CUSTOMIZE_REMOVE_SLOT / GuiLayouts.COLUMNS);
        }

        // Preview
        ItemStack preview = session.target();
        if (preview == null) {
            preview = new ItemStack(Material.GRAY_DYE);
        }
        // Stage trim onto preview if both chosen; the preview is cosmetic, so a bad target
        // simply keeps the untrimmed item rather than breaking the screen.
        TrimChoice choice = resolveTrimChoice(selectedTrimPattern, selectedTrimMaterial);
        if (choice != null) {
            try {
                CustomizeRules previewRules = rules();
                preview = applier(previewRules, new CosmeticValidator(previewRules))
                        .applyTrim(preview, choice.material(), choice.pattern());
            } catch (IllegalArgumentException ignored) {
                // Preview only: never fail the whole screen for an incompatible target.
            }
        }
        chrome.addItem(new GuiItem(preview), GuiLayouts.CUSTOMIZE_PREVIEW_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.CUSTOMIZE_PREVIEW_SLOT / GuiLayouts.COLUMNS);

        // Apply
        chrome.addItem(wrap(GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Apply",
                GuiTheme.dim("Commit staged changes")), (p, e) -> {
            SoundManager.playClick(p);
            if (writePending) return;
            CustomizeRules rules = rules();
            if (!rules.enabled()) {
                gui.kits().lang().send(p, "error.cosmetics-disabled");
                return;
            }
            UUID viewer = p.getUniqueId();
            if (gui.isCustomizeStale(viewer)) {
                gui.kits().lang().send(p, "error.cosmetics-kit-changed");
                return;
            }
            CosmeticValidator validator = new CosmeticValidator(rules);
            for (ItemStack item : session.working()) {
                if (validator.isShield(item) && validator.hasUnreadableBannerLayers(item)) {
                    gui.kits().lang().send(p, "error.cosmetics-banner-locked");
                    return;
                }
            }
            // Stage trim if both chosen before committing. A key that can no longer be resolved
            // is a failure, not a silent no-op that would save an untrimmed item.
            if (selectedTrimPattern != null && selectedTrimMaterial != null) {
                TrimChoice chosen = resolveTrimChoice(selectedTrimPattern, selectedTrimMaterial);
                ItemStack target = session.target();
                if (chosen == null || target == null || !validator.isArmor(target)) {
                    gui.kits().lang().send(p, chosen == null
                            ? "error.cosmetics-save-failed" : "error.cosmetics-not-armor");
                    return;
                }
                try {
                    session.stageTarget(applier(rules, validator)
                            .applyTrim(target, chosen.material(), chosen.pattern()));
                } catch (IllegalArgumentException ex) {
                    gui.kits().lang().send(p, "error.cosmetics-save-failed");
                    return;
                }
            }
            // Register without clearing a reload stale marker, then commit through the
            // outcome overload so the queued write can report back after close/quit.
            UUID operationId = gui.holdPendingCustomize(viewer, session);
            CustomizeService service = CustomizeService.create(gui);
            ApplyResult result = service.commit(p, kitSlot, session, success ->
                    Bukkit.getScheduler().runTask(gui.plugin(), () -> {
                        CustomizeSession taken = gui.takePendingCustomize(viewer, operationId);
                        if (taken == null) return; // consumed, mismatched, or invalidated by reload
                        if (!p.isOnline()) {
                            if (!success) {
                                gui.plugin().getLogger().warning(
                                        "Late kit cosmetics save failure for " + viewer);
                            }
                            return;
                        }
                        if (success) {
                            gui.kits().lang().send(p, "info.cosmetics-applied");
                            ItemEditorPage.openKit(gui, p, kitSlot);
                        } else {
                            gui.kits().lang().send(p, "error.cosmetics-save-failed");
                            CustomizePage.reopen(gui, p, kitSlot, taken);
                        }
                    }));
            if (result == ApplyResult.QUEUED) {
                writePending = true;
                gui.kits().lang().send(p, "info.cosmetics-save-queued");
                return; // the outcome callback closes the screen once the write resolves
            }
            gui.clearPendingCustomize(viewer);
            switch (result) {
                case PERMISSION_DENIED -> gui.kits().lang().send(p, "error.cosmetics-disabled");
                case KIT_CHANGED -> gui.kits().lang().send(p, "error.cosmetics-kit-changed");
                case INVALID_CONTENT -> gui.kits().lang().send(p, "error.cosmetics-name-invalid");
                case STORAGE_FAILED -> gui.kits().lang().send(p, "error.cosmetics-save-failed");
                case QUEUED -> { /* handled above */ }
            }
        }, player), GuiLayouts.CUSTOMIZE_APPLY_SLOT % GuiLayouts.COLUMNS, GuiLayouts.CUSTOMIZE_APPLY_SLOT / GuiLayouts.COLUMNS);

        // Tabs
        String[] tabs = {"Trim", "Banner", "Rename"};
        int[] tabSlots = {5, 6, 7};
        for (int i = 0; i < tabs.length; i++) {
            final String tab = tabs[i];
            chrome.addItem(wrap(GuiTheme.named(Material.PAPER, GuiTheme.AQUA + "<bold>", tab,
                    GuiTheme.hint("Click", "open")), (p, e) -> {
                SoundManager.playClick(p);
                if ("Banner".equals(tab)) {
                    // Validate first: a failed entry must not leave this screen in a mode with
                    // no picker and with the shield target selected by accident.
                    CustomizeRules tabRules = rules();
                    if (!tabRules.enabled()) {
                        gui.kits().lang().send(p, "error.cosmetics-disabled");
                        return;
                    }
                    CosmeticValidator validator = new CosmeticValidator(tabRules);
                    ItemStack shield = session.working()[CosmeticTarget.SHIELD.index()];
                    if (!validator.isShield(shield)) {
                        gui.kits().lang().send(p, "error.cosmetics-not-shield");
                        return;
                    }
                    if (validator.hasUnreadableBannerLayers(shield)) {
                        gui.kits().lang().send(p, "error.cosmetics-banner-locked");
                        return;
                    }
                    session.selectTarget(CosmeticTarget.SHIELD.index());
                    mode = Mode.BANNER;
                    BannerDesignerPage.open(gui, p, session, kitSlot);
                } else if ("Rename".equals(tab)) {
                    if (!rules().enabled()) {
                        gui.kits().lang().send(p, "error.cosmetics-disabled");
                        return;
                    }
                    mode = Mode.RENAME;
                    RenameTargetPage.open(gui, p, session, kitSlot);
                } else {
                    mode = Mode.TRIM;
                    refresh();
                }
            }, player), tabSlots[i] % GuiLayouts.COLUMNS, tabSlots[i] / GuiLayouts.COLUMNS);
        }

        // Target selector row 0
        CosmeticTarget[] targets = CosmeticTarget.values();
        for (int i = 0; i < targets.length; i++) {
            CosmeticTarget t = targets[i];
            Material iconMat;
            switch (t) {
                case BOOTS -> iconMat = Material.LEATHER_BOOTS;
                case LEGGINGS -> iconMat = Material.LEATHER_LEGGINGS;
                case CHESTPLATE -> iconMat = Material.LEATHER_CHESTPLATE;
                case HELMET -> iconMat = Material.LEATHER_HELMET;
                case SHIELD -> iconMat = Material.SHIELD;
                default -> iconMat = Material.ARMOR_STAND;
            }
            final int slotIndex = t.index();
            chrome.addItem(wrap(GuiTheme.named(iconMat, GuiTheme.TEXT + "<bold>", t.name(),
                    GuiTheme.hint("Click", "select")), (p, e) -> {
                SoundManager.playClick(p);
                session.selectTarget(slotIndex);
                clearTrimSelection();
                refresh();
            }, player), i % GuiLayouts.COLUMNS, i / GuiLayouts.COLUMNS);
        }
    }

    private void clearTrimSelection() {
        selectedTrimPattern = null;
        selectedTrimMaterial = null;
        trimMaterialStep = false;
        page = 0;
    }

    @Override
    protected void protect(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        chest.setOnClose(event -> {
            if (!(event.getPlayer() instanceof Player closer)) return;
            UUID viewer = closer.getUniqueId();
            gui.unregisterCustomizeScreen(viewer);
            if (writePending) {
                // The queued write keeps its clone; only feedback and pending state are dropped.
                gui.clearPendingCustomize(viewer);
            } else if (gui.isLastCustomizeScreen(viewer)) {
                // Opening a Banner/Rename tab registers the incoming screen before this one
                // closes, so only a genuine close (or Back, which discards explicitly) resets.
                session.discard();
            }
        });
    }
}
