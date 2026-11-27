package org.pvpcore.gui.kits.editor;

import org.bukkit.event.inventory.ClickType;

/**
 * Pure decisions for the raw-slot item interactions of the kit/ender-chest editors.
 *
 * <p>Each predicate answers "should the editor consume this click for its own action?" before
 * any inventory mutation happens. Keeping the rules here (rather than inside the click handler)
 * makes the cursor/drop safety guarantees testable without a live server.</p>
 *
 * <p>{@code start} is inclusive and {@code end} exclusive, so control slots immediately after
 * the content range never trigger an item action.</p>
 */
public final class ItemEditorActions {
    private ItemEditorActions() {
    }

    /** Shift-clicking a content slot opens that item's customization. */
    public static boolean customize(ClickType type, int raw, int start, int end) {
        return (type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT)
                && inContent(raw, start, end);
    }

    /**
     * Right-clicking a content slot copies the complete stack to the cursor, but only when the
     * cursor is empty -- otherwise the player's carried stack would be overwritten.
     */
    public static boolean copy(ClickType type, int raw, int start, int end, boolean cursorEmpty) {
        return type == ClickType.RIGHT && cursorEmpty && inContent(raw, start, end);
    }

    /** Drop / control-drop removes the whole stack from a content slot without a world drop. */
    public static boolean discard(ClickType type, int raw, int start, int end) {
        return (type == ClickType.DROP || type == ClickType.CONTROL_DROP)
                && inContent(raw, start, end);
    }

    private static boolean inContent(int raw, int start, int end) {
        return raw >= start && raw < end;
    }
}
