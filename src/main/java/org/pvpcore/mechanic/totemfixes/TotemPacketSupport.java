package org.pvpcore.mechanic.totemfixes;

import com.github.retrooper.packetevents.PacketEvents;

/**
 * Isolates every PacketEvents type from the mechanic's main class.
 * This class is only loaded after PacketEventsGate has confirmed that the runtime plugin
 * is present and supported, preserving PvPCore's optional soft-dependency behavior.
 *
 * <p><b>Registry exception (T52):</b> intentionally not migrated onto {@code PacketListenerRegistry}.
 * Anti-totem uses a distinct single-listener lifecycle/API shape and direct PacketEvents registration
 * semantics; keeping this isolated support avoids changing listener type/ownership contracts while
 * preserving exactly-once register/unregister behavior in {@code AntiTotemGhostMechanic}.</p>
 */
final class TotemPacketSupport {

    private TotemPacketSupport() {
    }

    static Object register(TotemSwapTracker tracker) {
        TotemPacketListener listener = new TotemPacketListener(tracker);
        PacketEvents.getAPI().getEventManager().registerListener(listener);
        return listener;
    }

    static void unregister(Object candidate) {
        if (candidate instanceof TotemPacketListener listener) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        }
    }
}
