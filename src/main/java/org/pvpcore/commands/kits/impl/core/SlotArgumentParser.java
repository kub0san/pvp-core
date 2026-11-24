package org.pvpcore.commands.kits.impl.core;

import com.google.common.primitives.Ints;
import org.jetbrains.annotations.Nullable;

public final class SlotArgumentParser {
    private SlotArgumentParser() {
    }

    public static @Nullable Integer parseSlot(String slotArgument) {
        return Ints.tryParse(slotArgument);
    }

    public static @Nullable Integer parseSlotInRange(String slotArgument, int min, int max) {
        Integer slot = parseSlot(slotArgument);
        if (slot == null || slot < min || slot > max) {
            return null;
        }
        return slot;
    }
}
