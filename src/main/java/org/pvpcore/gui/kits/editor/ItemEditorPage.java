package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.ItemFilter;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.kits.page.KitListPage;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.UUID;

/**
 * The framed item editor. Replaces {@code EditorGui}: the same kit, ender chest,
 * public kit and staff inspection screens, now on the framed v2 foundation.
 *
 * <p>Layout (6 rows):</p>
 * <pre>
 *   rows 0-3   the kit's main inventory (36 slots)
 *   row 4      the kit's armor, columns 0-4 (kit layouts) or a divider (EC layouts)
 *   row 5      the footer contract: armor labels, mode, import, clear, back
 * </pre>
 *
 * <p>The items themselves are written straight into the inventory after the
 * screen opens, never through a pane, so a redraw can never overwrite what the
 * player placed. The editor is saved when the screen closes.</p>
 */
public final class ItemEditorPage extends GuiScreen {
    private static final String PERMISSION = "perplayerkit.admin";

    private final EditorSpec spec;

    private ItemEditorPage(GUI gui, Player player, EditorSpec spec, GuiFrame.Density density) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, density, spec.permission(), spec.feature());
        this.spec = spec;
    }

    // ------------------------------------------------------------------ entry points

    /** The player's own kit in one slot. */
    public static void openKit(GUI gui, Player player, int slot) {
        KitsContext kits = gui.kits();
        ItemStack[] content = kits.itemFilter().filterItemStack(kits.kitManager().getPlayerKit(player.getUniqueId(), slot));
        EditorSpec spec = new EditorSpec(
                EditorSpec.title(kits, "gui.kit-editor-title", "slot", String.valueOf(slot)),
                "perplayerkit.kit", LocationFeature.KITS,
                GuiLayouts.KIT_CONTENT_START, kitLength(), true, content,
                new GUI.EditorContext(GUI.EditorType.KIT, slot, null, null, null));
        spec.armorLabels(true);
        spec.button(GuiLayouts.EDITOR_MODE_SLOT, new EditorSpec.Button(
                GuiTheme.named(Material.ENDER_CHEST, GuiTheme.AQUA + "<bold>", "Ender chest", GuiTheme.dim("Edit ender chest " + slot), GuiTheme.hint("Click", "switch")),
                (p, e) -> {
                    SoundManager.playClick(p);
                    gui.openEnderchestEditor(p, slot);
                }));
        spec.button(GuiLayouts.EDITOR_IMPORT_SLOT, EditorSpec.importButton(kits, "Import", "Copy what you carry",
                p -> p.getInventory().getContents(), GuiLayouts.KIT_CONTENT_START, kitLength()));
        spec.button(GuiLayouts.EDITOR_CLEAR_SLOT, EditorSpec.clearButton(GuiLayouts.KIT_CONTENT_START, kitLength()));
        spec.button(GuiLayouts.EDITOR_BACK_SLOT, EditorSpec.backButton("Saved automatically", p -> {
            gui.flushOpenEditor(p);
            KitListPage.open(gui, p, KitListPage.pageOfSlot(p, false, slot));
        }));
        new ItemEditorPage(gui, player, spec, GuiFrame.Density.EDITOR_ARMOR).show();
    }

    /** The player's own ender chest in one slot. */
    public static void openEnderchest(GUI gui, Player player, int slot) {
        KitsContext kits = gui.kits();
        ItemStack[] content = kits.itemFilter().filterItemStack(kits.kitManager().getPlayerEC(player.getUniqueId(), slot));
        EditorSpec spec = new EditorSpec(
                EditorSpec.title(kits, "gui.enderchest-editor-title", "slot", String.valueOf(slot)),
                "perplayerkit.enderchest", LocationFeature.ENDERCHESTS,
                GuiLayouts.EC_CONTENT_START, ecLength(), true, content,
                new GUI.EditorContext(GUI.EditorType.ENDERCHEST, slot, null, null, null));
        spec.button(GuiLayouts.EDITOR_MODE_SLOT, new EditorSpec.Button(
                GuiTheme.named(Material.CHEST, GuiTheme.AQUA + "<bold>", "Kit", GuiTheme.dim("Edit kit " + slot), GuiTheme.hint("Click", "switch")),
                (p, e) -> {
                    SoundManager.playClick(p);
                    gui.openKitEditor(p, slot);
                }));
        spec.button(GuiLayouts.EDITOR_IMPORT_SLOT, EditorSpec.importButton(kits, "Import", "Copy your ender chest",
                p -> p.getEnderChest().getContents(), GuiLayouts.EC_CONTENT_START, ecLength()));
        spec.button(GuiLayouts.EDITOR_CLEAR_SLOT, EditorSpec.clearButton(GuiLayouts.EC_CONTENT_START, ecLength()));
        spec.button(GuiLayouts.EDITOR_BACK_SLOT, EditorSpec.backButton("Saved automatically", p -> {
            gui.flushOpenEditor(p);
            KitListPage.open(gui, p, KitListPage.pageOfSlot(p, true, slot));
        }));
        new ItemEditorPage(gui, player, spec, GuiFrame.Density.EDITOR_EC).show();
    }

    /** A premade kit, edited by staff. */
    public static void openPublicKit(GUI gui, Player player, String kitId) {
        KitsContext kits = gui.kits();
        ItemStack[] content = kits.kitManager().getPublicKit(kitId);
        EditorSpec spec = new EditorSpec(
                EditorSpec.title(kits, "gui.public-kit-editor-title", "id", kitId),
                PERMISSION, null,
                GuiLayouts.KIT_CONTENT_START, kitLength(), true, content,
                new GUI.EditorContext(GUI.EditorType.PUBLIC_KIT, 0, kitId, null, null));
        spec.armorLabels(true);
        spec.button(GuiLayouts.EDITOR_IMPORT_SLOT, EditorSpec.importButton(kits, "Import", "Copy what you carry",
                p -> p.getInventory().getContents(), GuiLayouts.KIT_CONTENT_START, kitLength()));
        spec.button(GuiLayouts.EDITOR_CLEAR_SLOT, EditorSpec.clearButton(GuiLayouts.KIT_CONTENT_START, kitLength()));
        spec.button(GuiLayouts.EDITOR_BACK_SLOT, EditorSpec.backButton("Saved automatically", p -> {
            if (p.hasPermission("perplayerkit.editkitroom")) {
                gui.OpenPremadeKitSettings(p, kitId);
            } else {
                gui.OpenPublicKitMenu(p);
            }
        }));
        new ItemEditorPage(gui, player, spec, GuiFrame.Density.EDITOR_ARMOR).show();
    }

    /** Another player's kit, looked at by staff. */
    public static void openInspectKit(GUI gui, Player player, UUID target, String playerName, int slot) {
        KitsContext kits = gui.kits();
        KitManager kitManager = kits.kitManager();
        ItemStack[] content = kitManager.hasKit(target, slot)
                ? kitManager.getItemStackArrayById(target.toString() + slot) : null;
        boolean admin = player.hasPermission(PERMISSION);
        EditorSpec spec = new EditorSpec(
                EditorSpec.title(kits, "gui.inspect-kit-title", "player", playerName, "slot", String.valueOf(slot)),
                admin ? PERMISSION : "perplayerkit.staff", null,
                GuiLayouts.KIT_CONTENT_START, kitLength(), admin, content,
                new GUI.EditorContext(GUI.EditorType.INSPECT_KIT, slot, null, target, playerName));
        spec.armorLabels(true);
        spec.button(GuiLayouts.EDITOR_BACK_SLOT, EditorSpec.closeButton());
        if (admin) {
            spec.button(GuiLayouts.EDITOR_CLEAR_SLOT, EditorSpec.deleteButton(kits, "kit " + slot, playerName,
                    () -> {
                        kitManager.deleteKit(target, slot);
                        return "success.admin-kit-deleted";
                    }, slot));
        }
        new ItemEditorPage(gui, player, spec, GuiFrame.Density.EDITOR_ARMOR).show();
        SoundManager.playOpenGui(player);
    }

    /** Another player's ender chest, looked at by staff. */
    public static void openInspectEnderchest(GUI gui, Player player, UUID target, String playerName, int slot) {
        KitsContext kits = gui.kits();
        KitManager kitManager = kits.kitManager();
        ItemStack[] content = kitManager.getPlayerEC(target, slot);
        boolean admin = player.hasPermission(PERMISSION);
        EditorSpec spec = new EditorSpec(
                EditorSpec.title(kits, "gui.inspect-ec-title", "player", playerName, "slot", String.valueOf(slot)),
                admin ? PERMISSION : "perplayerkit.staff", null,
                GuiLayouts.EC_CONTENT_START, ecLength(), admin, content,
                new GUI.EditorContext(GUI.EditorType.INSPECT_ENDERCHEST, slot, null, target, playerName));
        spec.button(GuiLayouts.EDITOR_BACK_SLOT, EditorSpec.closeButton());
        if (admin) {
            spec.button(GuiLayouts.EDITOR_CLEAR_SLOT, EditorSpec.deleteButton(kits, "ender chest " + slot, playerName,
                    () -> {
                        kitManager.deleteEnderchest(target, slot);
                        return "success.admin-ec-deleted";
                    }, slot));
        }
        new ItemEditorPage(gui, player, spec, GuiFrame.Density.EDITOR_EC).show();
        SoundManager.playOpenGui(player);
    }

    private static int kitLength() {
        return GuiLayouts.KIT_CONTENT_END - GuiLayouts.KIT_CONTENT_START;
    }

    private static int ecLength() {
        return GuiLayouts.EC_CONTENT_END - GuiLayouts.EC_CONTENT_START;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected String title() {
        return spec.title();
    }

    @Override
    protected void renderContent(StaticPane content) {
        // The items live in the inventory, not in a pane. See afterShow.
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        int footerRow = editorFooterRow();
        boolean[] filled = new boolean[GuiLayouts.COLUMNS];

        if (spec.armorLabels()) {
            addLabel(chrome, 0, Material.CHAINMAIL_BOOTS, "Boots");
            addLabel(chrome, 1, Material.CHAINMAIL_LEGGINGS, "Leggings");
            addLabel(chrome, 2, Material.CHAINMAIL_CHESTPLATE, "Chestplate");
            addLabel(chrome, 3, Material.CHAINMAIL_HELMET, "Helmet");
            addLabel(chrome, 4, Material.SHIELD, "Offhand");
            for (int col = 0; col <= 4; col++) filled[col] = true;
        }

        // Every editor button lives in the footer row.
        for (int slot = 0; slot < GuiLayouts.MENU_SIZE; slot++) {
            EditorSpec.Button button = spec.button(slot);
            if (button == null) continue;
            if (!spec.editable() && (slot == GuiLayouts.EDITOR_IMPORT_SLOT || slot == GuiLayouts.EDITOR_MODE_SLOT)) continue;
            int col = slot % GuiLayouts.COLUMNS;
            chrome.addItem(wrap(button.item(), button.click(), player), col, slot / GuiLayouts.COLUMNS);
            filled[col] = true;
        }

        for (int col = 0; col < GuiLayouts.COLUMNS; col++) {
            if (filled[col]) continue;
            chrome.addItem(new GuiItem(GuiTheme.filler(kits())), col, footerRow);
        }
    }

    /** One armor label, placed by its absolute slot in the footer row. */
    private void addLabel(StaticPane chrome, int offset, Material icon, String name) {
        int slot = GuiLayouts.EDITOR_LABEL_START + offset;
        chrome.addItem(new GuiItem(GuiTheme.named(icon, GuiTheme.MUTED, name, GuiTheme.dim("Slot above"))),
                slot % GuiLayouts.COLUMNS, slot / GuiLayouts.COLUMNS);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void protect(ChestGui chest) {
        GuiGuard.editable(chest, spec.permission(), spec.feature(), spec::isEditableSlot);
    }

    @Override
    protected void configure(ChestGui chest) {
        chest.setOnClose(event -> {
            if (!(event.getPlayer() instanceof Player closer)) return;
            GUI.EditorContext context = GUI.getAndRemoveEditorContext(closer.getUniqueId());
            if (context == null) return;
            EditorSaver.save(kits(), closer, context, event.getInventory());
        });
    }

    // refresh() must not repaint here: it would discard the player's in-progress edits.
    @Override
    protected void afterInventory(Inventory inventory, boolean refresh) {
        if (refresh) {
            return;
        }
        ItemStack[] content = spec.content();
        for (int i = 0; i < spec.contentLength(); i++) {
            ItemStack item = content != null && i < content.length ? content[i] : null;
            inventory.setItem(spec.contentStart() + i, item);
        }
        // Must come after show(): opening the new inventory closes the previous one,
        // and that close would otherwise consume this context.
        GUI.setEditorContext(player, spec.context());
    }
}
