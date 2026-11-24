package org.pvpcore.mechanic.totemfixes;

/** Protocol button-byte decoding for quick-craft drags (kept separate for unit testing). */
final class QuickCraftButtons {

    private QuickCraftButtons() {
    }

    /**
     * The raw protocol button byte packs the drag phase in the low two bits (0 start, 1 add,
     * 2 end) and the mouse button in the upper bits. Only the END phase pairs with the single
     * InventoryDragEvent emitted for a quick-craft drag; {@code >> 2} selected the mouse
     * button instead and made ordinary drags invisible to the swap tracker.
     */
    static boolean isEnd(int button) {
        return (button & 3) == 2;
    }
}
