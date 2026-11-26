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
 * the Paper event and any legacy rebroadcast of the same message.</p>
 */
final class GuiChatPromptPacketListener extends SimplePacketListenerAbstract {
    private static final PacketListenerRegistry REGISTRY = new PacketListenerRegistry();

    /** Injected by the facade; returns true when the chat packet must be cancelled. */
    @FunctionalInterface
    interface Interceptor {
        boolean consume(UUID playerId, String message);
    }

    private final Interceptor interceptor;

    private GuiChatPromptPacketListener(Interceptor interceptor) {
        super(PacketListenerPriority.LOWEST);
        this.interceptor = interceptor;
    }

    /** Registers the listener. Returns false when PacketEvents refused it. */
    static boolean install(Interceptor interceptor) {
        try {
            return REGISTRY.register(new GuiChatPromptPacketListener(interceptor)) > 0;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /** Unregisters the listener; best-effort during shutdown. */
    static int stop() {
        return REGISTRY.stop();
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
