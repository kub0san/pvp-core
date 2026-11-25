package org.pvpcore.features.kits.cosmetics;

import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.KitContents;

import java.util.Arrays;
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
        // A kit that has never been saved is represented by a null/short array. Cosmetic GUIs
        // address all 41 kit slots, so normalize once here instead of special-casing each screen.
        ItemStack[] snapshot = normalize(source);
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
        ItemStack[] fresh = normalize(current);
        if (fresh.length != original.length) return false;
        for (int i = 0; i < original.length; i++) {
            if (!Objects.equals(original[i], fresh[i])) return false;
        }
        return true;
    }

    /**
     * Rebases this session onto the live cache after an unconfirmed write. The returned session
     * snapshots {@code current} again, so a retry is not rejected as KIT_CHANGED, and carries no
     * stale staged flags; a still-valid target selection is preserved. A null read (no manager
     * or no cached kit) has nothing to rebase onto, so the taken session is returned unchanged
     * and its staged edits survive.
     */
    public CustomizeSession rebaseFrom(ItemStack[] current) {
        if (current == null) return this;
        CustomizeSession rebased = new CustomizeSession(current);
        if (targetSlot >= 0 && targetSlot < rebased.working.length) {
            rebased.targetSlot = targetSlot;
        }
        return rebased;
    }

    private void requireTarget() {
        if (targetSlot < 0 || targetSlot >= working.length) {
            throw new IllegalStateException("No kit target selected");
        }
    }

    /**
     * Pads a null/short kit array to the 41 slots every cosmetic screen addresses. Longer arrays
     * are left alone so a genuine layout change is still reported as stale at Apply. Ownership
     * stays with the caller: the constructor takes its own clones and comparisons are read-only.
     */
    private static ItemStack[] normalize(ItemStack[] source) {
        int length = source == null ? 0 : source.length;
        return Arrays.copyOf(source == null ? new ItemStack[0] : source,
                Math.max(KitContents.INVENTORY_SIZE, length));
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
