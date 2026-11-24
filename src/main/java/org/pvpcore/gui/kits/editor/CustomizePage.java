package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.cosmetics.CosmeticTarget;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.cosmetics.ApplyResult;
import org.pvpcore.features.kits.cosmetics.CustomizeService;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

public final class CustomizePage extends GuiScreen {
    private final GUI gui;
    private final int kitSlot;
    private final CustomizeSession session;

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
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            session.discard();
            gui.flushOpenEditor(p);
            ItemEditorPage.openKit(gui, p, kitSlot);
        }, player), GuiLayouts.CUSTOMIZE_BACK_SLOT % GuiLayouts.COLUMNS, GuiLayouts.CUSTOMIZE_BACK_SLOT / GuiLayouts.COLUMNS);

        chrome.addItem(wrap(GuiTheme.named(Material.BARRIER, GuiTheme.MUTED + "<bold>", "Reset",
                GuiTheme.dim("Restore selected target")), (p, e) -> {
            SoundManager.playClick(p);
            if (session.targetSlot() >= 0) session.resetTarget();
            refresh();
        }, player), GuiLayouts.CUSTOMIZE_RESET_SLOT % GuiLayouts.COLUMNS, GuiLayouts.CUSTOMIZE_RESET_SLOT / GuiLayouts.COLUMNS);

        ItemStack preview = session.target();
        if (preview == null) {
            preview = new ItemStack(Material.GRAY_DYE);
        }
        chrome.addItem(new GuiItem(preview), GuiLayouts.CUSTOMIZE_PREVIEW_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.CUSTOMIZE_PREVIEW_SLOT / GuiLayouts.COLUMNS);

        chrome.addItem(wrap(GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Apply",
                GuiTheme.dim("Commit staged changes")), (p, e) -> {
            SoundManager.playClick(p);
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
