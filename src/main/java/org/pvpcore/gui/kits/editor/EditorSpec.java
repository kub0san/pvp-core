package org.pvpcore.gui.kits.editor;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.ItemFilter;
import org.pvpcore.features.kits.KitContents;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.base.GuiClick;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiConfirm;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.function.Supplier;

/**
 * The model of one item editor: which slots hold real items, which footer
 * buttons it has, and what happens when it is closed.
 *
 * <p>Replaces the inner {@code Screen} class of the old {@code EditorGui}. It
 * carries no rendering logic: {@link ItemEditorPage} draws it.</p>
 */
public final class EditorSpec {
    /** A footer button: its item and what it does. */
    public record Button(ItemStack item, GuiClick click) {
    }

    /** An action run on the player, without a click event. */
    @FunctionalInterface
    public interface Action {
        void run(Player player);
    }

    /** Reads the items an import copies from somewhere. */
    @FunctionalInterface
    public interface SourceReader {
        ItemStack[] read(Player player);
    }

    private final String title;
    private final String permission;
    private final LocationFeature feature;
    private final int contentStart;
    private final int contentLength;
    private final boolean editable;
    private final ItemStack[] content;
    private final GUI.EditorContext context;
    private final Button[] buttons = new Button[GuiLayouts.MENU_SIZE];
    private boolean armorLabels;

    public EditorSpec(String title, String permission, LocationFeature feature,
                      int contentStart, int contentLength, boolean editable,
                      ItemStack[] content, GUI.EditorContext context) {
        this.title = title;
        this.permission = permission;
        this.feature = feature;
        this.contentStart = contentStart;
        this.contentLength = contentLength;
        this.editable = editable;
        this.content = content;
        this.context = context;
    }

    public String title() {
        return title;
    }

    public String permission() {
        return permission;
    }

    public LocationFeature feature() {
        return feature;
    }

    public int contentStart() {
        return contentStart;
    }

    public int contentLength() {
        return contentLength;
    }

    public boolean editable() {
        return editable;
    }

    public ItemStack[] content() {
        return content;
    }

    public GUI.EditorContext context() {
        return context;
    }

    public boolean armorLabels() {
        return armorLabels;
    }

    public void armorLabels(boolean armorLabels) {
        this.armorLabels = armorLabels;
    }

    public Button button(int slot) {
        return buttons[slot];
    }

    public void button(int slot, Button button) {
        buttons[slot] = button;
    }

    /** True when the slot holds a real item of this editor. */
    public boolean isContentSlot(int slot) {
        return slot >= contentStart && slot < contentStart + contentLength;
    }

    /** True when the player may put an item there. */
    public boolean isEditableSlot(int slot) {
        return editable && isContentSlot(slot);
    }

    // ================================================================== buttons

    /** Copies the player's carried items (or ender chest) into the editor. */
    public static Button importButton(KitsContext kits, String title, String about, SourceReader reader, int start, int length) {
        return new Button(GuiTheme.named(Material.HOPPER, GuiTheme.GOLD + "<bold>", title, GuiTheme.dim(about), GuiTheme.hint("Click", "import")), (p, e) -> {
            SoundManager.playClick(p);
            ItemStack[] source = reader.read(p);
            ItemStack[] imported;
            ItemFilter filter = kits.itemFilter();
            if (filter.filtersImports()) {
                if (!filter.isReady()) {
                    kits.lang().send(p, "error.kitroom-not-ready");
                    return;
                }
                imported = filter.filterKit(source, p);
                if (imported == null) return;
            } else {
                imported = KitContents.copy(source);
            }
            Inventory inv = p.getOpenInventory().getTopInventory();
            for (int i = 0; i < length && i < imported.length; i++) {
                inv.setItem(start + i, imported[i]);
            }
        });
    }

    /** Empties the editable area on shift-click. */
    public static Button clearButton(int start, int length) {
        return new Button(GuiTheme.named(Material.BARRIER, GuiTheme.RED + "<bold>", "Clear", GuiTheme.dim("Requires shift-click"), GuiTheme.hint("Shift-click", "clear")), (p, e) -> {
            SoundManager.playClick(p);
            if (e.getClick().isShiftClick()) {
                GUI.markCleared(p);
                Inventory inv = p.getOpenInventory().getTopInventory();
                for (int i = 0; i < length; i++) {
                    inv.setItem(start + i, null);
                }
            }
        });
    }

    /** Back, with a line of reassurance under the name. */
    public static Button backButton(String reassurance, Action back) {
        return new Button(GuiTheme.named(Material.OAK_DOOR, GuiTheme.GREEN + "<bold>", "Back", GuiTheme.dim(reassurance)), (p, e) -> {
            SoundManager.playClick(p);
            back.run(p);
        });
    }

    /** Close, for screens that are only looked at. */
    public static Button closeButton() {
        return new Button(GuiTheme.named(Material.OAK_DOOR, GuiTheme.MUTED, "Close"), (p, e) -> {
            SoundManager.playClick(p);
            p.closeInventory();
            SoundManager.playCloseGui(p);
        });
    }

    /** Admin-only delete for an inspected player's kit or ender chest. Asks first. */
    public static Button deleteButton(KitsContext kits, String what, String playerName,
                                     Supplier<String> deleteAndGetMessageKey, int slot) {
        return new Button(GuiTheme.named(Material.LAVA_BUCKET, GuiTheme.RED + "<bold>", "Delete", GuiTheme.dim("Removes this " + what), GuiTheme.hint("Shift-click", "delete")), (p, e) -> {
            SoundManager.playClick(p);
            if (!e.getClick().isShiftClick()) return;
            GuiConfirm.open(p, "Delete " + what + "?",
                    new String[]{"This removes " + playerName + "'s " + what + "."}, true, "perplayerkit.admin",
                    () -> {
                        String messageKey = deleteAndGetMessageKey.get();
                        kits.lang().send(p, messageKey, "slot", String.valueOf(slot));
                        SoundManager.playSuccess(p);
                        GUI.markKitDeleted(p);
                    },
                    null);
        });
    }

    /** The coloured title of an editor screen. */
    public static String title(KitsContext kits, String key, String... pairs) {
        return GuiMenuFactory.langTitle(kits, key, pairs);
    }
}
