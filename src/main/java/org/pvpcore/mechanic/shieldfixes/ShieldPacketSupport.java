package org.pvpcore.mechanic.shieldfixes;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import org.bukkit.entity.Player;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.mechanic.PacketListenerRegistry;

import java.util.List;
import java.util.UUID;

/**
 * PacketEvents entry point for Shield Fixes. The package-private listener is
 * created only by {@link #start}, after the shared gate has passed, so
 * {@link ShieldFixesMechanic} can still be class-loaded when PacketEvents is
 * not installed.
 */
final class ShieldPacketSupport {

    private static final PacketListenerRegistry REGISTRY = new PacketListenerRegistry();

    private ShieldPacketSupport() {
    }

    /** Registers the packet listener and returns how many PacketEvents accepted. */
    static int start(PvPCorePlugin plugin, ShieldFixesMechanic mechanic, ShieldState state) {
        SimplePacketListenerAbstract listener = new ShieldPacketListener(mechanic, state);
        int count = REGISTRY.register(listener);
        if (mechanic.isDebug()) {
            plugin.getLogger().info("[debug] Registered Shield Fixes packet listener "
                    + listener.getClass().getSimpleName() + ".");
        }
        return count;
    }

    /** Unregisters every packet listener and returns how many were removed. */
    static int stop() {
        return REGISTRY.stop();
    }

    /**
     * Sends a hand-state metadata packet to the viewers whose last-sent hand
     * bits differ from the server's real state. With {@code force}, the packet is
     * sent to every tracking viewer regardless of the cache; this is used once
     * after a shield use, where the client can have missed the vanilla packet.
     *
     * @return number of viewers that received a packet
     */
    static int sendHandState(Player player, ShieldState state, byte handBits, boolean force,
                             ShieldFixesMechanic mechanic) {
        int entityId = player.getEntityId();
        int hand = handBits & ShieldState.HAND_MASK;
        int sent = 0;
        for (Player viewer : player.getTrackedBy()) {
            UUID viewerId = viewer.getUniqueId();
            boolean hasPrevious = state.hasSentHand(entityId, viewerId);
            byte previous = state.lastSentHand(entityId, viewerId);
            if (!force && hasPrevious && (previous & ShieldState.HAND_MASK) == hand) {
                continue;
            }
            // Preserve unrelated flags, such as the spin-attack bit, from the last
            // packet this viewer received. With no history, only the hand bits are set.
            byte flags = (byte) ((hasPrevious ? previous & ~ShieldState.HAND_MASK : 0) | hand);
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(
                    entityId,
                    List.of(new EntityData<>(ShieldState.HAND_STATE_INDEX,
                            EntityDataTypes.BYTE, flags)));
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
                state.recordSentHand(entityId, viewerId, flags);
                sent++;
            } catch (Throwable t) {
                // One disconnected viewer must not stop the refresh for the rest.
                mechanic.debug("Could not send hand state to " + viewer.getName() + ": " + t);
            }
        }
        return sent;
    }
}
