package org.pvpcore.gui.base;

import org.bukkit.inventory.ItemStack;
import org.pvpcore.gui.kits.editor.EditorHintLore;

/**
 * Display-only clones of staged items for GUI showcase and preview slots.
 *
 * <p>The clone is written into a read-only GUI slot: it is never staged, saved or
 * handed back to the caller's working copy. Private editor hint lore is stripped so
 * hints cannot leak into a preview, but no theme styling or metadata is added.</p>
 */
public final class GuiPreviewItems {
    private GuiPreviewItems() {
    }

    /**
     * Returns a new display clone of {@code source} with editor hint lore removed.
     * The source is never mutated. Null and air inputs return null.
     */
    public static ItemStack displayClone(ItemStack source) {
        if (source == null || source.getType().isAir()) {
            return null;
        }
        ItemStack clone = source.clone();
        EditorHintLore.strip(clone);
        return clone;
    }
}
