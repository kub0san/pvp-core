package org.pvpcore.mechanic.legacypearls;

import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.mechanic.PacketListenerRegistry;

/**
 * Every PacketEvents type lives behind this class so {@link LegacyPearlsMechanic} can be
 * loaded and registered when PacketEvents is not installed.
 */
final class LegacyPearlPacketSupport {

    private static final PacketListenerRegistry REGISTRY = new PacketListenerRegistry();

    private LegacyPearlPacketSupport() {
    }

    /** Registers the position-packet listener. Returns {@code false} when none was accepted. */
    static boolean start(PvPCorePlugin plugin, LegacyPearlsMechanic mechanic) {
        SimplePacketListenerAbstract listener = new LegacyPearlPositionListener(mechanic);
        int count = REGISTRY.register(listener);
        if (mechanic.isDebug()) {
            plugin.getLogger().info("[debug] Registered packet listener "
                    + listener.getClass().getSimpleName() + ".");
        }
        return count > 0;
    }

    /** Unregisters every packet listener and returns how many were removed. */
    static int stop() {
        return REGISTRY.stop();
    }
}
