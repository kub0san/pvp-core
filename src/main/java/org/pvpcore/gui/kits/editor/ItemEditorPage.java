package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.ItemFilter;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
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

    private final GUI gui;
    private final EditorSpec spec;

    private ItemEditorPage(GUI gui, Player player, EditorSpec spec, GuiFrame.Density density) {
        super(gui, player, GuiLayouts.ROWS_EDITOR, density, spec.permission(), spec.feature());
        this.gui = gui;
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
        if (org.pvpcore.config.kits.CustomizeRules.parse(
                kits.config().getConfigurationSection("customize")).enabled()) {
            spec.button(GuiLayouts.EDITOR_CUSTOMIZE_SLOT, new EditorSpec.Button(
                    GuiTheme.named(Material.ARMOR_STAND, GuiTheme.AQUA + "<bold>", "Customize", GuiTheme.dim("Trims, banners, names"), GuiTheme.hint("Click", "open")),
                    (p, e) -> {
                        SoundManager.playClick(p);
                        gui.flushOpenEditor(p);
                        CustomizePage.open(gui, p, slot, new org.pvpcore.features.kits.cosmetics.CustomizeSession(gui.kits().kitManager().getPlayerKit(p.getUniqueId(), slot)));
                    }));
        }
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
        GuiGuard.editable(chest, spec.permission(), spec.feature(), spec::isEditableSlot,
                topSize(), this::handleContentClick, this::handleGlobalClick);
    }

    /**
     * Raw-slot item actions. These are composed into the guard, never assigned over it, so the
     * permission, collect-to-cursor, non-editable-slot and drag rules stay intact. Cancelling
     * happens only when an action actually claims the click: ordinary pickups and placements in
     * the content range must keep working.
     */
    private void handleContentClick(InventoryClickEvent event) {
        if (!spec.editable()) {
            return;
        }
        int raw = event.getRawSlot();
        int start = spec.contentStart();
        int end = start + spec.contentLength();
        ClickType click = event.getClick();

        if (ItemEditorActions.customize(click, raw, start, end)) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                openCustomize();
            }
            return;
        }
        boolean cursorEmpty = event.getCursor() == null || event.getCursor().getType().isAir();
        if (ItemEditorActions.copy(click, raw, start, end, cursorEmpty)) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                // The slot holds a display clone; copy the original stack, never the hints.
                ItemStack copy = current.clone();
                EditorHintLore.strip(copy);
                event.setCursor(copy);
            }
            return;
        }
        if (ItemEditorActions.discard(click, raw, start, end)) {
            event.setCancelled(true);
            // Remove the complete stack without letting vanilla spawn an ItemEntity.
            event.setCurrentItem(null);
        }
    }

    /** Stops offhand swaps and bottom-inventory number-key swaps from reaching the editor's slots. */
    private void handleGlobalClick(InventoryClickEvent event) {
        // Offhand swaps must be cancelled regardless of which inventory the click originated in:
        // a top-slot F-click can still move an item into or out of the editor's offhand slot.
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() < topSize()) {
            return;
        }
        InventoryAction action = event.getAction();
        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            event.setCancelled(true);
        }
    }

    /** Shift-click entry into cosmetics. The item-specific hub arrives with the UX redesign. */
    private void openCustomize() {
        GUI.EditorContext context = spec.context();
        if (!spec.editable() || context == null || context.type() != GUI.EditorType.KIT) {
            return;
        }
        gui.flushOpenEditor(player);
        CustomizePage.open(gui, player, context.slot(),
                new CustomizeSession(gui.kits().kitManager().getPlayerKit(player.getUniqueId(), context.slot())));
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
            // Only the visible clone carries hints; the stored item stays untouched.
            ItemStack display = item == null ? null : EditorHintLore.apply(item.clone());
            inventory.setItem(spec.contentStart() + i, display);
        }
        // Must come after show(): opening the new inventory closes the previous one,
        // and that close would otherwise consume this context.
        GUI.setEditorContext(player, spec.context());
    }
}
