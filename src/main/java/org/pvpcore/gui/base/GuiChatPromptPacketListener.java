package org.pvpcore.gui.base;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import org.pvpcore.mechanic.PacketListenerRegistry;

import java.util.UUID;

/**
 * Packet-level half of the private chat prompt. Every PacketEvents type is confined to
 * this class: {@link GuiChatPrompt} resolves it only after
 * {@link org.pvpcore.mechanic.PacketEventsGate#ready()} so servers without the optional
 * dependency keep working with the dual-event fallback.
 *
 * <p>{@code LOWEST} runs before Bukkit events are fired, so cancelling here stops both
 * the Paper event and any legacy rebroadcast of the same message. The registry is an
 * instance field owned by the facade's active listener, never a mutable static.</p>
 */
final class GuiChatPromptPacketListener extends SimplePacketListenerAbstract {
    /** Injected by the facade; returns true when the chat packet must be cancelled. */
    @FunctionalInterface
    interface Interceptor {
        boolean consume(UUID playerId, String message);
    }

    private final PacketListenerRegistry registry = new PacketListenerRegistry();
    private final Interceptor interceptor;

    GuiChatPromptPacketListener(Interceptor interceptor) {
        super(PacketListenerPriority.LOWEST);
        this.interceptor = interceptor;
    }

    /** Registers this listener. Returns false when PacketEvents refused it. */
    boolean install() {
        try {
            return registry.register(this) > 0;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /** Unregisters this listener; best-effort during shutdown. */
    void stop() {
        registry.stop();
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.isCancelled() || event.getPacketType() != PacketType.Play.Client.CHAT_MESSAGE) {
            return;
        }
        try {
            var user = event.getUser();
            UUID id = user == null ? null : user.getUUID();
            if (id == null) {
                return;
            }
            String message = new WrapperPlayClientChatMessage(event).getMessage();
            if (message == null || message.startsWith("/")) {
                return;
            }
            if (interceptor.consume(id, message)) {
                event.setCancelled(true);
            }
        } catch (Throwable malformedPacket) {
            // A bad packet must never break the network pipeline; the fallback handlers
            // remain available for any Bukkit event that is still delivered.
        }
    }
}
