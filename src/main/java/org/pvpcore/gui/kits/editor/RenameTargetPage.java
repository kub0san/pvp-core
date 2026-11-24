package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.cosmetics.CosmeticApplier;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiChatPrompt;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

/**
 * Ghost picker over all 41 saved kit slots. Empty slots are display-only; clicking a
 * non-empty slot opens the chat prompt to rename that item.
 */
public final class RenameTargetPage extends GuiScreen {
    private final GUI gui;
    private final CustomizeSession session;
    private final int kitSlot;

    private RenameTargetPage(GUI gui, Player player, CustomizeSession session, int kitSlot) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, org.pvpcore.gui.base.GuiFrame.Density.NONE,
                "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.kitSlot = kitSlot;
        gui.registerCustomizeScreen(player.getUniqueId());
    }

    public static void open(GUI gui, Player player, CustomizeSession session, int kitSlot) {
        if (session == null || gui == null || player == null) return;
        if (gui.kits() == null) return;
        if (!org.pvpcore.commands.kits.core.ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return;
        CustomizeRules rules = CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize"));
        if (!rules.enabled()) {
            gui.kits().lang().send(player, "error.cosmetics-disabled");
            return;
        }
        new RenameTargetPage(gui, player, session, kitSlot).show();
    }

    @Override
    protected String title() {
        if (kits() == null) return "Rename Item";
        return GuiMenuFactory.sectionTitle(kits(), GuiTheme.ACCENT + "<bold>Rename Item");
    }

    @Override
    protected void renderContent(StaticPane content) {
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // Back button
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            CustomizePage.reopen(gui, p, kitSlot, session);
        }, player), GuiLayouts.RENAME_BACK_SLOT % GuiLayouts.COLUMNS, GuiLayouts.RENAME_BACK_SLOT / GuiLayouts.COLUMNS);

        // Ghost slots 0..40
        for (int i = GuiLayouts.RENAME_TARGET_FIRST; i <= GuiLayouts.RENAME_TARGET_LAST; i++) {
            final int targetSlot = i;
            ItemStack item = session.working()[targetSlot];
            boolean empty = item == null || item.getType().isAir();
            Material icon = empty ? Material.GRAY_DYE : item.getType();
            String name = empty ? "Empty" : (item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().displayName() != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()) : "Item"
                    : item.getType().name());
            chrome.addItem(wrap(GuiTheme.named(icon, empty ? GuiTheme.MUTED : GuiTheme.TEXT + "<bold>", name,
                    empty ? GuiTheme.dim("No item") : GuiTheme.hint("Click", "rename")), (p, e) -> {
                if (empty) return;
                SoundManager.playClick(p);
                session.selectTarget(targetSlot);
                UUID opId = gui.setPendingCustomize(p.getUniqueId(), session);
                CustomizeRules rules = CustomizeRules.parse(gui.kits().config().getConfigurationSection("customize"));
                CosmeticValidator validator = new CosmeticValidator(rules);
                CosmeticApplier applier = new CosmeticApplier(rules, validator);
                GuiChatPrompt.ask(gui.plugin(), p, "Type a new name (MiniMessage), or 'cancel'",
                        text -> {
                            CustomizeSession taken = gui.takePendingCustomize(p.getUniqueId(), opId);
                            if (taken == null) return;
                            try {
                                // applyName parses once and clones before writing; the session
                                // stays untouched when validation fails.
                                ItemStack current = taken.target();
                                if (current != null) {
                                    taken.stageTarget(applier.applyName(current, text));
                                }
                            } catch (CosmeticValidator.BlockedTagException ex) {
                                gui.kits().lang().send(p, "error.cosmetics-name-tag-blocked");
                            } catch (IllegalArgumentException ex) {
                                gui.kits().lang().send(p, "error.cosmetics-name-invalid");
                            }
                            CustomizePage.reopen(gui, p, kitSlot, taken);
                        },
                        () -> {
                            gui.takePendingCustomize(p.getUniqueId(), opId);
                            CustomizePage.reopen(gui, p, kitSlot, session);
                        });
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
            if (event.getPlayer() instanceof Player closer) {
                gui.unregisterCustomizeScreen(closer.getUniqueId());
            }
        });
    }
}
