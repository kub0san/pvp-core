package org.pvpcore.features.kits.cosmetics;

import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.KitContents;

import java.util.Objects;

/**
 * Staged cosmetic edits for one personal kit. The constructor takes two independent clones of
 * the source: {@code original} is the snapshot used for stale detection and Reset, while
 * {@code working} is mutated by staged edits. Neither the source array nor its items escape.
 */
public final class CustomizeSession {
    private final ItemStack[] original;
    private final ItemStack[] working;
    private final boolean[] staged;
    private int targetSlot = -1;

    public CustomizeSession(ItemStack[] source) {
        ItemStack[] snapshot = source == null ? new ItemStack[0] : source;
        this.original = KitContents.copy(snapshot);
        this.working = KitContents.copy(snapshot);
        this.staged = new boolean[snapshot.length];
    }

    /** Selects the kit slot that subsequent target operations act on. */
    public void selectTarget(int slot) {
        if (slot < 0 || slot >= working.length) {
            throw new IndexOutOfBoundsException("Kit slot out of bounds: " + slot);
        }
        this.targetSlot = slot;
    }

    public int targetSlot() {
        return targetSlot;
    }

    /** An owned copy of the staged target, or null when none is selected or the slot is empty. */
    public ItemStack target() {
        if (targetSlot < 0 || targetSlot >= working.length) return null;
        return copy(working[targetSlot]);
    }

    /** Stages a clone of {@code item}; callers keep ownership of their instance. */
    public void stageTarget(ItemStack item) {
        requireTarget();
        working[targetSlot] = copy(item);
        staged[targetSlot] = true;
    }

    /** Restores the selected slot from the original snapshot. */
    public void resetTarget() {
        requireTarget();
        working[targetSlot] = copy(original[targetSlot]);
        staged[targetSlot] = false;
    }

    /** Reverts every staged slot; used by Back so an abandoned screen never persists anything. */
    public void discard() {
        for (int i = 0; i < working.length; i++) {
            working[i] = copy(original[i]);
            staged[i] = false;
        }
    }

    /** An owned copy of the whole working kit, ready to hand to the save seam. */
    public ItemStack[] working() {
        return KitContents.copy(working);
    }

    public boolean isStaged(int slot) {
        return slot >= 0 && slot < staged.length && staged[slot];
    }

    /**
     * True when {@code current} still matches the snapshot taken at construction. Staged edits
     * deliberately do not count: a fresh kit read must equal what the player started from.
     */
    public boolean matchesCurrent(ItemStack[] current) {
        if (current == null || current.length != original.length) return false;
        for (int i = 0; i < original.length; i++) {
            if (!Objects.equals(original[i], current[i])) return false;
        }
        return true;
    }

    private void requireTarget() {
        if (targetSlot < 0 || targetSlot >= working.length) {
            throw new IllegalStateException("No kit target selected");
        }
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
