package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Registry;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.cosmetics.ApplyResult;
import org.pvpcore.features.kits.cosmetics.CosmeticCatalog;
import org.pvpcore.features.kits.cosmetics.CosmeticTarget;
import org.pvpcore.features.kits.cosmetics.CustomizeService;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiPager;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;

/**
 * Staged cosmetic editor for one personal kit.
 */
public final class CustomizePage extends GuiScreen {
    private final GUI gui;
    private final int kitSlot;
    private final CustomizeSession session;

    // Trim staging state. Browsing never mutates the session; both must be chosen before Apply.
    private String selectedTrimPattern;
    private String selectedTrimMaterial;
    private boolean trimMaterialStep;

    private CustomizePage(GUI gui, Player player, int kitSlot, CustomizeSession session) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, org.pvpcore.gui.base.GuiFrame.Density.NONE,
                "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.kitSlot = kitSlot;
        this.session = session;
    }

    public static void open(GUI gui, Player player, int kitSlot, CustomizeSession session) {
        if (session == null) return;
        if (!org.pvpcore.commands.kits.core.ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return;
        if (gui.kits() == null || !gui.kits().itemFilter().isReady()) {
            gui.kits().lang().send(player, "error.kitroom-not-ready");
            return;
        }
        CustomizeRules rules = CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize"));
        if (!rules.enabled()) {
            gui.kits().lang().send(player, "error.cosmetics-disabled");
            return;
        }
        new CustomizePage(gui, player, kitSlot, session).show();
    }

    public static void reopen(GUI gui, Player player, int kitSlot, CustomizeSession session) {
        open(gui, player, kitSlot, session);
    }

    @Override
    protected String title() {
        return GuiTheme.ACCENT + "<bold>Customize Kit " + kitSlot;
    }

    @Override
    protected void renderContent(StaticPane content) {
        // Trim picker: pattern step or material step, paged.
        CustomizeRules rules = CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize"));
        CosmeticCatalog catalog = CosmeticCatalog.fromRegistries(rules);
        List<CosmeticCatalog.Entry> entries = trimMaterialStep
                ? catalog.trimMaterials() : catalog.trimPatterns();
        GuiPager pager = GuiPager.of(0, entries.size(), GuiLayouts.COLUMNS * 4);
        int from = pager.from();
        int to = pager.to(entries.size());
        int row = 1;
        int col = 0;
        for (int i = from; i < to; i++) {
            CosmeticCatalog.Entry entry = entries.get(i);
            final String key = entry.key();
            ItemStack icon = entry.icon().clone();
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
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // Back
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            session.discard();
            gui.flushOpenEditor(p);
            ItemEditorPage.openKit(gui, p, kitSlot);
        }, player), GuiLayouts.CUSTOMIZE_BACK_SLOT % GuiLayouts.COLUMNS, GuiLayouts.CUSTOMIZE_BACK_SLOT / GuiLayouts.COLUMNS);

        // Reset
        chrome.addItem(wrap(GuiTheme.named(Material.BARRIER, GuiTheme.MUTED + "<bold>", "Reset",
                GuiTheme.dim("Restore selected target")), (p, e) -> {
            SoundManager.playClick(p);
            if (session.targetSlot() >= 0) session.resetTarget();
            selectedTrimPattern = null;
            selectedTrimMaterial = null;
            trimMaterialStep = false;
            refresh();
        }, player), GuiLayouts.CUSTOMIZE_RESET_SLOT % GuiLayouts.COLUMNS, GuiLayouts.CUSTOMIZE_RESET_SLOT / GuiLayouts.COLUMNS);

        // Preview
        ItemStack preview = session.target();
        if (preview == null) {
            preview = new ItemStack(Material.GRAY_DYE);
        }
        // Stage trim onto preview if both chosen
        if (selectedTrimPattern != null && selectedTrimMaterial != null && preview.getItemMeta() instanceof ArmorMeta) {
            try {
                RegistryAccess access = RegistryAccess.registryAccess();
                Registry<TrimPattern> patternReg = access.getRegistry(RegistryKey.TRIM_PATTERN);
                Registry<TrimMaterial> materialReg = access.getRegistry(RegistryKey.TRIM_MATERIAL);
                TrimPattern tp = patternReg.get(NamespacedKey.fromString(selectedTrimPattern));
                TrimMaterial tm = materialReg.get(NamespacedKey.fromString(selectedTrimMaterial));
                if (tp != null && tm != null) {
                    ArmorMeta am = (ArmorMeta) preview.getItemMeta();
                    am.setTrim(new ArmorTrim(tm, tp));
                    preview.setItemMeta(am);
                }
            } catch (RuntimeException ignored) { }
        }
        chrome.addItem(new GuiItem(preview), GuiLayouts.CUSTOMIZE_PREVIEW_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.CUSTOMIZE_PREVIEW_SLOT / GuiLayouts.COLUMNS);

        // Apply
        chrome.addItem(wrap(GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Apply",
                GuiTheme.dim("Commit staged changes")), (p, e) -> {
            SoundManager.playClick(p);
            // Stage trim if both chosen before committing
            if (selectedTrimPattern != null && selectedTrimMaterial != null) {
                ItemStack target = session.target();
                if (target != null && target.getItemMeta() instanceof ArmorMeta) {
                    try {
                        RegistryAccess access = RegistryAccess.registryAccess();
                        Registry<TrimPattern> patternReg = access.getRegistry(RegistryKey.TRIM_PATTERN);
                        Registry<TrimMaterial> materialReg = access.getRegistry(RegistryKey.TRIM_MATERIAL);
                        TrimPattern tp = patternReg.get(NamespacedKey.fromString(selectedTrimPattern));
                        TrimMaterial tm = materialReg.get(NamespacedKey.fromString(selectedTrimMaterial));
                        if (tp != null && tm != null) {
                            ArmorMeta am = (ArmorMeta) target.getItemMeta();
                            am.setTrim(new ArmorTrim(tm, tp));
                            target.setItemMeta(am);
                            session.stageTarget(target);
                        }
                    } catch (RuntimeException ignored) { }
                }
            }
            CustomizeService service = CustomizeService.create(gui);
            ApplyResult result = service.commit(p, kitSlot, session);
            switch (result) {
                case QUEUED -> {
                    gui.kits().lang().send(p, "info.cosmetics-save-queued");
                    gui.clearPendingCustomize(p.getUniqueId());
                    ItemEditorPage.openKit(gui, p, kitSlot);
                }
                case PERMISSION_DENIED -> gui.kits().lang().send(p, "error.cosmetics-disabled");
                case KIT_CHANGED -> gui.kits().lang().send(p, "error.cosmetics-kit-changed");
                case INVALID_CONTENT -> gui.kits().lang().send(p, "error.cosmetics-name-invalid");
                case STORAGE_FAILED -> gui.kits().lang().send(p, "error.cosmetics-save-failed");
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
                    if (session.targetSlot() != CosmeticTarget.SHIELD.index()) {
                        session.selectTarget(CosmeticTarget.SHIELD.index());
                    }
                    ItemStack target = session.target();
                    if (target == null || target.getType().isAir()) {
                        gui.kits().lang().send(p, "error.cosmetics-not-shield");
                        return;
                    }
                    BannerDesignerPage.open(gui, p, session);
                } else if ("Rename".equals(tab)) {
                    RenameTargetPage.open(gui, p, session, kitSlot);
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
                selectedTrimPattern = null;
                selectedTrimMaterial = null;
                trimMaterialStep = false;
                refresh();
            }, player), i % GuiLayouts.COLUMNS, i / GuiLayouts.COLUMNS);
        }
    }

    @Override
    protected void protect(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        chest.setOnClose(event -> {
            if (!(event.getPlayer() instanceof Player closer)) return;
            session.discard();
        });
    }
}
