package org.pvpcore.mechanic.fastercrystals;

/**
 * Per-player packet state. Written from the network thread, read from the main thread, so
 * every shared field is volatile.
 *
 * <p>Each arm swing gets its own {@link Swing} object. That keeps the inventory-drop mark
 * attached to the click that produced it even when the player swings again before the first
 * main-thread task runs. {@link LastPacketListener} marks the current swing when the packet
 * after it is an inventory drop or creative inventory action.</p>
 */
final class PlayerPacketState {

    private volatile AnimPacket lastPacket = AnimPacket.MISC;
    private volatile Swing swing;

    AnimPacket lastPacket() {
        return lastPacket;
    }

    void lastPacket(AnimPacket packet) {
        this.lastPacket = packet;
    }

    /**
     * Captures the packet that preceded this swing and makes the new swing current.
     * The returned object is the one the attack task re-checks before hitting.
     */
    Swing beginSwing() {
        Swing current = new Swing(lastPacket);
        swing = current;
        return current;
    }

    Swing swing() {
        return swing;
    }

    /** One arm swing, from the packet that preceded it to the final pre-hit check. */
    static final class Swing {

        private final AnimPacket previous;
        private volatile boolean ignore;

        private Swing(AnimPacket previous) {
            this.previous = previous;
        }

        AnimPacket previous() {
            return previous;
        }

        boolean ignore() {
            return ignore;
        }

        void ignore(boolean ignore) {
            this.ignore = ignore;
        }
    }
}
