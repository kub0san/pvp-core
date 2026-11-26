package org.pvpcore.gui.base;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.util.kits.LocationFeature;

import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Click protection for every screen.
 *
 * <p>Three shapes of screen exist:</p>
 * <ul>
 *   <li>{@link #readOnly} -- a screen of buttons and display items. The
 *       permission is checked on every click (pass null to skip the check),
 *       and nothing can be moved in or out of the screen. This replaces what
 *       KitMenuCloseListener#canUseMenu did for canvas menus.</li>
 *   <li>{@link #editable} -- a screen where the player may move items (the
 *       editors). Only the editable slots accept clicks, everything else is
 *       cancelled, and "collect to cursor" is always cancelled because it
 *       could pull footer buttons into the cursor. Dragging is cancelled when
 *       it touches a slot of the screen that is not editable; a drag that only
 *       involves the player's own inventory is fine.</li>
 *   <li>{@link #takeOnly} -- a screen whose items the player may only take out
 *       of (the kit room). Placement into the screen is always cancelled.</li>
 * </ul>
 *
 * <p>Dispatch order in the inventory-framework (IF 0.12.x) is:
 * {@code setOnGlobalClick} -> {@code setOnTopClick}/{@code setOnBottomClick} ->
 * <em>then the {@code GuiItem} handlers</em>. Cancelling an event therefore never
 * stops a button from firing; it only stops the vanilla inventory action. That is
 * why a blanket {@code setOnTopClick(event -> event.setCancelled(true))} is the
 * right read-only guard, not a bug.</p>
 */
public final class GuiGuard {
    private GuiGuard() {
    }

    public static void readOnly(ChestGui chest, String permission, LocationFeature feature, int rows) {
        int size = rows * 9;

        chest.setOnGlobalClick(event -> {
            if (!(event.getWhoClicked() instanceof Player clicker)) {
                event.setCancelled(true);
                return;
            }
            if (permission != null && !ActionGuards.allowed(clicker, permission, feature)) {
                event.setCancelled(true);
                clicker.closeInventory();
                return;
            }
            // Shift-clicks from the player's inventory and double-click "collect to cursor"
            // would otherwise move items in or out of this screen.
            InventoryAction action = event.getAction();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
        });
        chest.setOnTopClick(event -> event.setCancelled(true));
        chest.setOnGlobalDrag(event -> {
            for (int raw : event.getRawSlots()) {
                if (raw < size) {
                    event.setCancelled(true);
                    return;
                }
            }
        });
    }

    /**
     * Click protection for screens whose items the player may only take out of,
     * never put into (the kit room).
     *
     * <ul>
     *   <li>the permission is checked on every click, and the screen closes when it fails;</li>
     *   <li>{@code PICKUP_*} works, so the player can take items;</li>
     *   <li>{@code MOVE_TO_OTHER_INVENTORY} works from the screen into the player's
     *       inventory, and is cancelled from the player's inventory into the screen;</li>
     *   <li>every placement action onto a screen slot ({@code PLACE_*}, {@code SWAP_WITH_CURSOR},
     *       {@code HOTBAR_SWAP}, {@code HOTBAR_MOVE_AND_READD}) is cancelled, so nothing can be
     *       put in -- not even onto a takeable slot with the cursor or the number keys;</li>
     *   <li>{@code COLLECT_TO_CURSOR} is always cancelled;</li>
     *   <li>only the {@code takeableSlot}s accept clicks at all; everything else
     *       (the footer) is cancelled;</li>
     *   <li><strong>every</strong> drag touching the screen is cancelled. A drag from
     *       the player's inventory onto a takeable slot would otherwise succeed --
     *       the take-out rule has to hold for drags too, not just clicks.</li>
     * </ul>
     *
     * @param takeableSlot tells whether a raw slot may be emptied by the player
     */
    public static void takeOnly(ChestGui chest, String permission, LocationFeature feature,
                                IntPredicate takeableSlot) {
        takeOnly(chest, permission, feature, takeableSlot, GuiLayouts.MENU_SIZE);
    }

    /**
     * Same contract as the six-row {@link #takeOnly(ChestGui, String, LocationFeature, IntPredicate)},
     * for screens with a different top-inventory size.
     */
    public static void takeOnly(ChestGui chest, String permission, LocationFeature feature,
                                IntPredicate takeableSlot, int topSize) {
        chest.setOnGlobalClick(event -> {
            if (!(event.getWhoClicked() instanceof Player clicker)) {
                event.setCancelled(true);
                return;
            }
            if (!ActionGuards.allowed(clicker, permission, feature)) {
                event.setCancelled(true);
                clicker.closeInventory();
                return;
            }
            InventoryAction action = event.getAction();
            int raw = event.getRawSlot();
            if (action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                return;
            }
            // Shift-click from the player's inventory into the screen.
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && raw >= topSize) {
                event.setCancelled(true);
                return;
            }
            // Any placement onto a screen slot: cursor, cursor-swap or number keys.
            if (raw < topSize) {
                switch (action) {
                    case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR,
                         HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> event.setCancelled(true);
                    default -> { }
                }
            }
        });
        chest.setOnTopClick(event -> {
            if (!takeableSlot.test(event.getRawSlot())) {
                event.setCancelled(true);
            }
        });
        chest.setOnGlobalDrag(event -> {
            for (int raw : event.getRawSlots()) {
                // Every drag into the screen is a placement, wherever it lands.
                if (raw >= 0 && raw < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        });
    }

    /**
     * Click protection for screens that move items, such as the editors.
     *
     * <p>An editor is the one screen where the player may move items, so the usual
     * read-only guard does not apply. Instead:</p>
     * <ul>
     *   <li>the permission is checked on every click, and the screen closes when it fails;</li>
     *   <li>"collect to cursor" is always cancelled, because it could pull the
     *       footer buttons out of the screen;</li>
     *   <li>only the editable slots accept clicks, everything else is cancelled;</li>
     *   <li>dragging is cancelled when it touches a slot of the screen that is not
     *       editable; a drag that only involves the player's own inventory is fine;</li>
     *   <li>shift-clicking <em>from the player's inventory into the screen</em> is
     *       always cancelled. Vanilla would insert the item at the first free slot,
     *       which may be a footer button, an armor label or the divider -- never a
     *       slot the editor thinks is editable. Taking items out (origin inside the
     *       screen) is unaffected.</li>
     * </ul>
     *
     * @param editableSlot tells whether a raw slot may hold a player-placed item
     */
    public static void editable(ChestGui chest, String permission, LocationFeature feature,
                                IntPredicate editableSlot) {
        editable(chest, permission, feature, editableSlot, GuiLayouts.MENU_SIZE, null, null);
    }

    /**
     * Same contract as the six-row {@link #editable(ChestGui, String, LocationFeature, IntPredicate)},
     * but derives the top inventory from {@code topSize} and composes two extra handlers after the
     * built-in cancellations: {@code extraTop} receives top-inventory clicks and {@code extraGlobal}
     * receives every click. A null handler is simply skipped.
     */
    public static void editable(ChestGui chest, String permission, LocationFeature feature,
                                IntPredicate editableSlot, int topSize,
                                Consumer<InventoryClickEvent> extraTop,
                                 Consumer<InventoryClickEvent> extraGlobal) {
        chest.setOnGlobalClick(event -> {
            if (!(event.getWhoClicked() instanceof Player clicker)) {
                event.setCancelled(true);
                return;
            }
            if (!ActionGuards.allowed(clicker, permission, feature)) {
                event.setCancelled(true);
                clicker.closeInventory();
                return;
            }
            InventoryAction action = event.getAction();
            // Double-click "collect to cursor" could pull footer items into the cursor.
            if (action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                return;
            }
            // Shift-click from the player's inventory: refuse, whatever the destination would be.
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && event.getRawSlot() >= topSize) {
                event.setCancelled(true);
            }
            if (extraGlobal != null) {
                extraGlobal.accept(event);
            }
        });
        chest.setOnTopClick(event -> {
            if (!editableSlot.test(event.getRawSlot())) {
                event.setCancelled(true);
            }
            if (extraTop != null) {
                extraTop.accept(event);
            }
        });
        chest.setOnGlobalDrag(event -> {
            for (int raw : event.getRawSlots()) {
                // Only the slots of the screen itself are checked. A drag that
                // starts in the player's own inventory is allowed, exactly as
                // the old editor did it.
                if (raw >= 0 && raw < topSize && !editableSlot.test(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
        });
    }
}
