package org.pvpcore.mechanic.fastercrystals;

import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.mechanic.PacketListenerRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every PacketEvents type lives behind this class so {@link FasterCrystalsMechanic} can be
 * loaded and registered when PacketEvents is not installed.
 */
final class CrystalPacketSupport {

    private static final PacketListenerRegistry REGISTRY = new PacketListenerRegistry();

    private CrystalPacketSupport() {
    }

    /** Registers the packet listeners and returns how many PacketEvents accepted. */
    static int start(PvPCorePlugin plugin, FasterCrystalsMechanic mechanic,
                     CrystalTracker tracker, Map<UUID, PlayerPacketState> states) {
        PickableResolver.resolve();
        if (mechanic.isDebug()) {
            plugin.getLogger().info("[debug] isPickable() reflection "
                    + (PickableResolver.isResolved()
                    ? "resolved." : "unavailable; treating every entity as pickable."));
        }

        List<SimplePacketListenerAbstract> created = List.of(
                new CrystalAttackListener(plugin, mechanic, tracker, states),
                new CrystalPlaceListener(plugin, mechanic, tracker),
                new LastPacketListener(mechanic, states)
        );
        int count = REGISTRY.registerAll(created);
        if (mechanic.isDebug()) {
            for (SimplePacketListenerAbstract listener : created) {
                plugin.getLogger().info("[debug] Registered packet listener "
                        + listener.getClass().getSimpleName() + ".");
            }
        }
        return count;
    }

    /** Unregisters every packet listener and returns how many were removed. */
    static int stop() {
        return REGISTRY.stop();
    }
}
